package com.naman.rupeeflow.domain.accounting

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.*
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FinancialRegressionAndLedgerInvariantsTest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendGroupRepository: FriendGroupRepository
    private lateinit var backupRestoreRepository: BackupRestoreRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        friendRepository = FriendRepositoryImpl(database.friendDao())
        transactionRepository = TransactionRepositoryImpl(
            database.transactionDao(),
            database.categoryDao(),
            database.friendDao()
        )
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())
        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        friendGroupRepository = FriendGroupRepositoryImpl(database.friendGroupDao())
        backupRestoreRepository = BackupRestoreRepository(
            database = database,
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            categoryRepository = categoryRepository,
            friendGroupRepository = friendGroupRepository,
            context = context
        )

        runBlocking {
            database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Test 57: Friend balances computed by LedgerCalculator match exactly before and after
    @Test
    fun testFriendBalancesUnchangedByPersonalAccounting() {
        val friend1 = Friend(id = 10L, name = "Aarav")
        val friend2 = Friend(id = 20L, name = "Bhavna")

        // Transaction 1: Me paid Rs 3,000, split equally between Me, Aarav, Bhavna (1000 each)
        // Aarav owes Me 1000, Bhavna owes Me 1000
        val txn1 = Transaction(
            id = 1L,
            title = "Dinner",
            amountPaise = 300000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            paidByName = "Me",
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 10L, friendName = "Aarav", shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 20L, friendName = "Bhavna", shareAmountPaise = 100000L)
            )
        )

        // Transaction 2: Aarav paid Rs 1,500, split equally between Me and Aarav (750 each)
        // I owe Aarav 750
        val txn2 = Transaction(
            id = 2L,
            title = "Cab",
            amountPaise = 150000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = 10L,
            paidByName = "Aarav",
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 75000L),
                TransactionParticipant(friendId = 10L, friendName = "Aarav", shareAmountPaise = 75000L)
            )
        )

        val transactions = listOf(txn1, txn2)
        val computedFriends = LedgerCalculator.computeFriendLedgers(
            friends = listOf(friend1, friend2),
            activeTransactions = transactions,
            settlements = emptyList()
        )

        val computedAarav = computedFriends.first { it.id == 10L }
        val computedBhavna = computedFriends.first { it.id == 20L }

        // Aarav: owes 1000, I owe 750 -> Net: Aarav owes Me 250 (25000 paise)
        assertEquals(25000L, computedAarav.totalTheyOweMePaise)
        assertEquals(0L, computedAarav.totalIOweThemPaise)
        assertEquals(25000L, computedAarav.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, computedAarav.status)

        // Bhavna: owes 1000 -> Net: Bhavna owes Me 1000 (100000 paise)
        assertEquals(100000L, computedBhavna.totalTheyOweMePaise)
        assertEquals(0L, computedBhavna.totalIOweThemPaise)
        assertEquals(100000L, computedBhavna.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, computedBhavna.status)

        // Note that personal financial aggregator computes personal expenses cleanly without changing friend balances:
        assertEquals(100000L, PersonalFinancialAggregator.calculatePersonalExpense(txn1))
        assertEquals(75000L, PersonalFinancialAggregator.calculatePersonalExpense(txn2))
    }

    // Test 58: Settlement calculations unchanged
    @Test
    fun testSettlementCalculationsBetweenFriendsPreserved() {
        val friend = Friend(id = 10L, name = "Aarav")

        // Me paid Rs 1,000, Aarav owes Rs 1,000
        val txn = Transaction(
            id = 1L,
            title = "Movie",
            amountPaise = 100000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 10L, friendName = "Aarav", shareAmountPaise = 100000L)
            )
        )

        // Aarav settles Rs 1,000 to Me
        val settlement = Settlement(
            id = 1L,
            payerId = 10L,
            receiverId = null,
            amountPaise = 100000L,
            settlementDate = System.currentTimeMillis()
        )

        val computedFriends = LedgerCalculator.computeFriendLedgers(
            friends = listOf(friend),
            activeTransactions = listOf(txn),
            settlements = listOf(settlement)
        )

        val settledAarav = computedFriends.first()
        assertEquals(0L, settledAarav.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, settledAarav.status)
    }

    // Test 59: Original gross transaction amount preserved
    @Test
    fun testOriginalGrossTransactionAmountNeverMutated() {
        val txn = Transaction(
            id = 1L,
            title = "Grand Feast",
            amountPaise = 160500L, // Rs 1,605.00
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 20063L),
                TransactionParticipant(friendId = 10L, friendName = "Rahul", shareAmountPaise = 140437L)
            )
        )

        // Verify gross amountPaise remains 160500L
        assertEquals(160500L, txn.amountPaise)
        // User personal expense is 20063L
        assertEquals(20063L, txn.userExpensePaise)
        assertEquals(20063L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
        // Calling aggregator does not mutate original amount
        assertEquals(160500L, txn.amountPaise)
    }

    // Test 60: Participant share totals sum to original amount
    @Test
    fun testParticipantShareTotalsSumToOriginalAmount() {
        val txn = Transaction(
            id = 1L,
            title = "Shared Bill",
            amountPaise = 727700L, // Rs 7,277.00
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 513983L),
                TransactionParticipant(friendId = 10L, friendName = "Friend 1", shareAmountPaise = 113717L),
                TransactionParticipant(friendId = 11L, friendName = "Friend 2", shareAmountPaise = 100000L)
            )
        )

        val sumShares = txn.participants.sumOf { it.shareAmountPaise }
        assertEquals(txn.amountPaise, sumShares)
        assertEquals(727700L, sumShares)
    }

    // Test 61: Database migration 7 to 8 runs cleanly
    @Test
    fun testDatabaseMigration7To8PassesCleanly() {
        val tempDbFile = File.createTempFile("test_migration_invariant", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Create basic tables
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS transactions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                title TEXT NOT NULL,
                                description TEXT NOT NULL,
                                amountPaise INTEGER NOT NULL,
                                categoryId INTEGER NOT NULL,
                                transactionDate INTEGER NOT NULL,
                                paymentMethod TEXT NOT NULL,
                                accountSource TEXT,
                                paidByFriendId INTEGER,
                                splitMethod TEXT NOT NULL,
                                transactionType TEXT NOT NULL,
                                transactionStatus TEXT NOT NULL,
                                source TEXT NOT NULL,
                                merchantName TEXT,
                                referenceNumber TEXT,
                                expenseGroupId INTEGER,
                                expenseGroupName TEXT,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
                            )
                        """.trimIndent())

                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS pending_transactions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                detectedAmountPaise INTEGER NOT NULL,
                                detectedType TEXT NOT NULL,
                                detectedBank TEXT,
                                detectedAccount TEXT,
                                detectedMerchant TEXT,
                                referenceNumber TEXT,
                                rawContent TEXT NOT NULL,
                                timestamp INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                source TEXT NOT NULL
                            )
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = helper.writableDatabase
        RupeeFlowDatabase.MIGRATION_7_8.migrate(db)

        // Insert and verify columns exist and work
        db.execSQL("""
            INSERT INTO pending_transactions (detectedAmountPaise, detectedType, detectedBank, detectedAccount, detectedMerchant, phoneNumber, upiId, referenceNumber, rawContent, timestamp, status, source)
            VALUES (50000, 'EXPENSE', 'HDFC', 'XX1234', 'Swiggy', '9876543210', 'swiggy@icici', 'REF123', 'Paid 500', 1000, 'PENDING', 'SMS')
        """.trimIndent())

        val cursor = db.query("SELECT phoneNumber, upiId FROM pending_transactions WHERE referenceNumber = 'REF123'")
        assertTrue(cursor.moveToFirst())
        assertEquals("9876543210", cursor.getString(0))
        assertEquals("swiggy@icici", cursor.getString(1))
        cursor.close()
        db.close()
    }

    // Test 62: Backup and restore roundtrip preserves split details, expense groups, and personal share calculations
    @Test
    fun testBackupAndRestoreRoundtripPreservesPersonalSharesAndExpenseGroups() = runBlocking {
        val friend = Friend(name = "Neha")
        val savedFriendId = friendRepository.addFriend(friend)

        database.expenseGroupDao().insertExpenseGroup(ExpenseGroupEntity(id = 5L, name = "Weekend Getaway"))

        val txn = Transaction(
            title = "Resort Trip",
            amountPaise = 600000L, // Rs 6,000
            categoryId = 1L,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            expenseGroupId = 5L,
            expenseGroupName = "Weekend Getaway",
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 200000L),
                TransactionParticipant(friendId = savedFriendId, friendName = "Neha", shareAmountPaise = 400000L)
            )
        )
        transactionRepository.saveTransaction(txn)

        // Export backup JSON
        val exportedJson = backupRestoreRepository.exportToJson()
        assertTrue(exportedJson.contains("Resort Trip"))

        // Clear database
        database.clearAllTables()
        assertEquals(0, transactionRepository.getAllTransactionsList().size)

        // Restore backup
        val restoreSuccess = backupRestoreRepository.restoreFromJson(exportedJson)
        assertTrue(restoreSuccess)

        // Verify restored transactions
        val restoredTxns = transactionRepository.getAllTransactionsList()
        val restoredTxn = restoredTxns.first { it.title == "Resort Trip" }

        assertEquals(600000L, restoredTxn.amountPaise)
        assertEquals(2, restoredTxn.participants.size)

        // Verify personal share calculation on restored transaction
        assertEquals(200000L, PersonalFinancialAggregator.calculatePersonalExpense(restoredTxn))
        assertEquals(200000L, restoredTxn.userExpensePaise)
    }
}

