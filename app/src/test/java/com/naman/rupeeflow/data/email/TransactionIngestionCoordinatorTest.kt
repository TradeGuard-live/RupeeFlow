package com.naman.rupeeflow.data.email

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.domain.model.TransactionSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TransactionIngestionCoordinatorTest {
    private lateinit var database: RupeeFlowDatabase
    private lateinit var coordinator: TransactionIngestionCoordinator

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            RupeeFlowDatabase::class.java
        ).allowMainThreadQueries().build()
        coordinator = TransactionIngestionCoordinator(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sameReferenceFromSmsAndEmailIsInsertedOnlyOnce() = runBlocking {
        val now = System.currentTimeMillis()
        val smsId = coordinator.insertIfNew(pending("123456789012", TransactionSource.SMS, now))
        val emailId = coordinator.insertIfNew(pending("123456789012", TransactionSource.EMAIL, now + 1000))
        assertNotNull(smsId)
        assertNull(emailId)
        assertEquals(1, database.pendingTransactionDao().getAllDetectedTransactionsList().size)
    }

    @Test
    fun differentReferencesWithSameAmountAreBothRetained() = runBlocking {
        val now = System.currentTimeMillis()
        val first = coordinator.insertIfNew(pending("123456789012", TransactionSource.EMAIL, now))
        val second = coordinator.insertIfNew(pending("987654321098", TransactionSource.SMS, now + 1000))
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(2, database.pendingTransactionDao().getAllDetectedTransactionsList().size)
    }

    @Test
    fun repeatedGmailMessageIsClaimedEvenWithoutReference() = runBlocking {
        val now = System.currentTimeMillis()
        val first = coordinator.insertIfNew(pending(null, TransactionSource.EMAIL, now), "gmail-message-1")
        val second = coordinator.insertIfNew(pending(null, TransactionSource.EMAIL, now), "gmail-message-1")
        assertNotNull(first)
        assertNull(second)
    }

    private fun pending(ref: String?, source: TransactionSource, time: Long) =
        PendingTransactionEntity(
            detectedAmountPaise = 25000L,
            detectedBank = "HDFC Bank",
            detectedMerchant = "Merchant",
            referenceNumber = ref,
            upiReferenceNumber = ref,
            rawContent = "HDFC UPI Rs 250",
            timestamp = time,
            source = source
        )
}
