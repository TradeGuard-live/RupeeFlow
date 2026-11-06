package com.naman.rupeeflow

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.*
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.SplitMethod
import com.naman.rupeeflow.domain.model.TransactionSource
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentationTest {

    private lateinit var database: RupeeFlowDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRoomDatabaseCreationAndDefaultCategories() = runBlocking {
        val categories = database.categoryDao().getAllCategoriesList()
        assertTrue("Default categories must be seeded", categories.isNotEmpty())
        assertTrue("Food category must exist", categories.any { it.name == "Food" })
        assertTrue("Rent & Housing must exist", categories.any { it.name == "Rent & Housing" })
    }

    @Test
    fun testFriendAndGroupCreationWithForeignKeys() = runBlocking {
        val friendId = database.friendDao().insertFriend(
            FriendEntity(
                name = "Aarav Sharma",
                phoneNumber = "9876543210"
            )
        )
        assertTrue(friendId > 0L)

        val groupId = database.friendGroupDao().insertGroup(
            FriendGroupEntity(
                name = "Flatmates",
                iconName = "home",
                colorHex = "#10B981"
            )
        )
        assertTrue(groupId > 0L)

        database.friendGroupDao().insertMembers(
            listOf(FriendGroupMemberEntity(groupId = groupId, friendId = friendId))
        )

        val members = database.friendGroupDao().getMembersForGroupList(groupId)
        assertEquals(1, members.size)
        assertEquals(friendId, members[0].friendId)
    }

    @Test
    fun testTransactionAndParticipantAtomicity() = runBlocking {
        val friendId = database.friendDao().insertFriend(
            FriendEntity(name = "Priya Mehta", phoneNumber = "9123456780")
        )

        val txnEntity = TransactionEntity(
            title = "Weekend Grocery",
            amountPaise = 200000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE,
            source = TransactionSource.MANUAL
        )

        val participants = listOf(
            TransactionParticipantEntity(
                transactionId = 0L,
                friendId = friendId,
                shareAmountPaise = 100000L,
                percentage = 50.0,
                splitMethod = SplitMethod.EQUAL
            ),
            TransactionParticipantEntity(
                transactionId = 0L,
                friendId = null, // ME
                shareAmountPaise = 100000L,
                percentage = 50.0,
                splitMethod = SplitMethod.EQUAL
            )
        )

        val txnId = database.transactionDao().saveTransactionWithParticipants(txnEntity, participants)
        assertTrue(txnId > 0L)

        val savedWithParts = database.transactionDao().getTransactionById(txnId)
        assertNotNull(savedWithParts)
        assertEquals("Weekend Grocery", savedWithParts?.transaction?.title)
        assertEquals(200000L, savedWithParts?.transaction?.amountPaise)
        assertEquals(2, savedWithParts?.participants?.size)
    }

    @Test
    fun testSmartInputUsageRankingPersistence() = runBlocking {
        val inputDao = database.inputUsageDao()
        inputDao.recordUsage("CATEGORY", "Food")
        inputDao.recordUsage("CATEGORY", "Food")
        inputDao.recordUsage("CATEGORY", "Travel")

        val usages = inputDao.getUsagesForFieldList("CATEGORY")
        assertEquals(2, usages.size)
        assertEquals("Food", usages[0].optionId)
        assertEquals(2L, usages[0].usageCount)
        assertEquals("Travel", usages[1].optionId)
        assertEquals(1L, usages[1].usageCount)
    }
}
