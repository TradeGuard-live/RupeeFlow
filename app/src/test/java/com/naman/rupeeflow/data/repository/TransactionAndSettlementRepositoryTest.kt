package com.naman.rupeeflow.data.repository

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TransactionAndSettlementRepositoryTest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var settlementRepository: SettlementRepository

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )

        settlementRepository = SettlementRepositoryImpl(
            settlementDao = database.settlementDao(),
            friendDao = database.friendDao()
        )

        runBlocking {
            database.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000", isDefault = true))
            database.friendDao().insertFriend(FriendEntity(id = 1L, name = "Ronit", phoneNumber = "9876543210", email = null, notes = null, createdAt = 1000L, isArchived = false))
            database.friendDao().insertFriend(FriendEntity(id = 2L, name = "Priya", phoneNumber = "9876543211", email = null, notes = null, createdAt = 1000L, isArchived = false))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTransactionEdit_PreservesIdAndCreatedAt_UpdatesParticipantsAtomically() = runBlocking {
        val initialTxn = Transaction(
            title = "Dinner",
            description = "Swiggy order",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = 2000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            ),
            createdAt = 1500L
        )

        val insertedId = transactionRepository.saveTransaction(initialTxn)
        assertTrue(insertedId > 0L)

        val retrievedBefore = transactionRepository.getTransactionById(insertedId)
        assertNotNull(retrievedBefore)
        assertEquals(100000L, retrievedBefore!!.amountPaise)
        assertEquals(1500L, retrievedBefore.createdAt)
        assertEquals(2, retrievedBefore.participants.size)

        // Edit transaction: Amount changes to ₹1,500, new participant added (Priya)
        val editedTxn = retrievedBefore.copy(
            title = "Grand Dinner",
            amountPaise = 150000L,
            participants = listOf(
                TransactionParticipant(transactionId = insertedId, friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(transactionId = insertedId, friendId = 1L, shareAmountPaise = 50000L),
                TransactionParticipant(transactionId = insertedId, friendId = 2L, shareAmountPaise = 50000L)
            )
        )

        val updatedId = transactionRepository.saveTransaction(editedTxn)
        assertEquals(insertedId, updatedId) // Must preserve the same ID!

        val retrievedAfter = transactionRepository.getTransactionById(insertedId)
        assertNotNull(retrievedAfter)
        assertEquals("Grand Dinner", retrievedAfter!!.title)
        assertEquals(150000L, retrievedAfter.amountPaise)
        assertEquals(1500L, retrievedAfter.createdAt) // Preserved!
        assertTrue(retrievedAfter.updatedAt >= retrievedBefore.updatedAt) // Updated!
        assertEquals(3, retrievedAfter.participants.size) // Atomically replaced!
    }

    @Test
    fun testTransactionEdit_RejectsMismatchedParticipantShares() = runBlocking {
        val initialTxn = Transaction(
            title = "Lunch",
            amountPaise = 100000L,
            categoryId = 1L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            )
        )
        val insertedId = transactionRepository.saveTransaction(initialTxn)

        // Attempt edit where shares total ₹900 (90000 paise) but amount is ₹1,000 (100000 paise)
        val invalidTxn = initialTxn.copy(
            id = insertedId,
            participants = listOf(
                TransactionParticipant(transactionId = insertedId, friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(transactionId = insertedId, friendId = 1L, shareAmountPaise = 50000L)
            )
        )

        try {
            transactionRepository.saveTransaction(invalidTxn)
            fail("Expected IllegalArgumentException for mismatched participant shares")
        } catch (_: IllegalArgumentException) {
            // Success
        }

        // Assert database was completely rolled back and previous state preserved
        val retrieved = transactionRepository.getTransactionById(insertedId)
        assertNotNull(retrieved)
        assertEquals(50000L, retrieved!!.participants[0].shareAmountPaise)
        assertEquals(50000L, retrieved.participants[1].shareAmountPaise)
    }

    @Test
    fun testSettlementEdit_PreservesIdCreatedAtAndReversalStatus() = runBlocking {
        val initialSettlement = Settlement(
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            paymentMethod = PaymentMethod.UPI,
            referenceNumber = "REF123",
            notes = "Initial settlement",
            isReversed = false,
            createdAt = 1500L
        )

        val insertedId = settlementRepository.recordSettlement(initialSettlement)
        assertTrue(insertedId > 0L)

        // Reverse the settlement to verify reversal status preservation during edit
        settlementRepository.reverseSettlement(insertedId)
        val reversed = settlementRepository.getSettlementById(insertedId)
        assertNotNull(reversed)
        assertTrue(reversed!!.isReversed)

        // Edit the settlement amount and reference
        val edited = reversed.copy(
            amountPaise = 75000L,
            referenceNumber = "REF999_EDITED",
            notes = "Updated note"
        )
        settlementRepository.updateSettlement(edited)

        val retrievedAfter = settlementRepository.getSettlementById(insertedId)
        assertNotNull(retrievedAfter)
        assertEquals(insertedId, retrievedAfter!!.id) // ID preserved
        assertEquals(75000L, retrievedAfter.amountPaise) // Amount updated
        assertEquals("REF999_EDITED", retrievedAfter.referenceNumber) // Ref updated
        assertEquals(1500L, retrievedAfter.createdAt) // CreatedAt preserved
        assertTrue(retrievedAfter.isReversed) // Reversal status strictly preserved!
    }

    @Test
    fun testSettlementEdit_ValidationRejectsInvalidEntities() = runBlocking {
        val settlement = Settlement(
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L
        )
        val sId = settlementRepository.recordSettlement(settlement)

        // Invalid: Amount 0
        try {
            settlementRepository.updateSettlement(settlement.copy(id = sId, amountPaise = 0L))
            fail("Expected exception for 0 amount")
        } catch (_: IllegalArgumentException) {}

        // Invalid: Both sides Me
        try {
            settlementRepository.updateSettlement(settlement.copy(id = sId, payerId = null, receiverId = null))
            fail("Expected exception for both sides Me")
        } catch (_: IllegalArgumentException) {}

        // Invalid: Payer equals receiver
        try {
            settlementRepository.updateSettlement(settlement.copy(id = sId, payerId = 1L, receiverId = 1L))
            fail("Expected exception for payer == receiver")
        } catch (_: IllegalArgumentException) {}
    }
}

