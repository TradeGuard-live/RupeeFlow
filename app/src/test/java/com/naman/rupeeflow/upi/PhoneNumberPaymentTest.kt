package com.naman.rupeeflow.upi

import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.FriendRepositoryImpl
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.SettlementRepositoryImpl
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.SettlementPaymentStatus
import com.naman.rupeeflow.domain.model.SettlementVerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PhoneNumberPaymentTest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var upiSettlementManager: UpiSettlementManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        friendRepository = FriendRepositoryImpl(database.friendDao())
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao(), context)
        upiSettlementManager = UpiSettlementManager(settlementRepository, null, friendRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Phone number cleaning and normalization
    @Test
    fun test01_phoneCleaningAndNormalization() {
        assertEquals("9876543210", UpiValidator.cleanPhoneNumber("+91 98765-43210"))
        assertEquals("9876543210", UpiValidator.cleanPhoneNumber("+919876543210"))
        assertEquals("9876543210", UpiValidator.cleanPhoneNumber("919876543210"))
        assertEquals("9876543210", UpiValidator.cleanPhoneNumber("09876543210"))
        assertEquals("9876543210", UpiValidator.cleanPhoneNumber("9876543210"))
        assertEquals("8123456789", UpiValidator.cleanPhoneNumber(" 81234 56789 "))

        assertNull(UpiValidator.cleanPhoneNumber("12345"))
        assertNull(UpiValidator.cleanPhoneNumber(""))
        assertNull(UpiValidator.cleanPhoneNumber(null))
    }

    // 2. Phone number validity checks
    @Test
    fun test02_phoneNumberValidation() {
        assertTrue(UpiValidator.isValidPhoneNumber("9876543210"))
        assertTrue(UpiValidator.isValidPhoneNumber("+91 98765 43210"))
        assertTrue(UpiValidator.isValidPhoneNumber("08123456789"))
        assertTrue(UpiValidator.isValidPhoneNumber("7000000000"))
        assertTrue(UpiValidator.isValidPhoneNumber("6111111111"))

        // Rejects numbers starting with 0-5 (not valid Indian mobile prefixes)
        assertFalse(UpiValidator.isValidPhoneNumber("5123456789"))
        assertFalse(UpiValidator.isValidPhoneNumber("1234567890"))
        assertFalse(UpiValidator.isValidPhoneNumber("invalid-phone"))
        assertFalse(UpiValidator.isValidPhoneNumber(""))
    }

    // 3. Phone number display formatting
    @Test
    fun test03_phoneDisplayFormatting() {
        assertEquals("+91 98765 43210", UpiValidator.formatPhoneDisplay("9876543210"))
        assertEquals("+91 98765 43210", UpiValidator.formatPhoneDisplay("+91 98765-43210"))
    }

    // 4. PhonePe VPA derivation
    @Test
    fun test04_phonePeVpaDerivation() {
        val phonePeVpa = UpiIntentBuilder.buildPhoneNumberVpa("9876543210", UpiIntentBuilder.PHONEPE_PACKAGE)
        assertEquals("9876543210@ybl", phonePeVpa)

        val withPrefix = UpiIntentBuilder.buildPhoneNumberVpa("+91 98765 43210", UpiIntentBuilder.PHONEPE_PACKAGE)
        assertEquals("9876543210@ybl", withPrefix)
    }

    // 5. BHIM VPA derivation
    @Test
    fun test05_bhimVpaDerivation() {
        val bhimVpa = UpiIntentBuilder.buildPhoneNumberVpa("9876543210", UpiIntentBuilder.BHIM_PACKAGE)
        assertEquals("9876543210@upi", bhimVpa)

        val defaultVpa = UpiIntentBuilder.buildPhoneNumberVpa("9876543210", null)
        assertEquals("9876543210@upi", defaultVpa)
    }

    // 6. Phone number payment URI building for PhonePe
    @Test
    fun test06_phonePeUriConstruction() {
        val uri = UpiIntentBuilder.buildPhoneNumberUpiUri(
            phoneNumber = "9876543210",
            receiverName = "Rohan Sharma",
            amountPaise = 50000L, // Rs. 500.00
            targetPackage = UpiIntentBuilder.PHONEPE_PACKAGE
        )

        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=9876543210@ybl"))
        assertTrue(uri.contains("pn=Rohan%20Sharma"))
        assertTrue(uri.contains("am=500.00"))
        assertTrue(uri.contains("cu=INR"))
        // Strict P2P rule: 'tn' and 'tr' MUST be omitted
        assertFalse(uri.contains("&tn="))
        assertFalse(uri.contains("&tr="))
    }

    // 7. Phone number payment URI building for BHIM
    @Test
    fun test07_bhimUriConstruction() {
        val uri = UpiIntentBuilder.buildPhoneNumberUpiUri(
            phoneNumber = "9876543210",
            receiverName = "Priya Patel",
            amountPaise = 12550L, // Rs. 125.50
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE
        )

        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=9876543210@upi"))
        assertTrue(uri.contains("pn=Priya%20Patel"))
        assertTrue(uri.contains("am=125.50"))
        assertTrue(uri.contains("cu=INR"))
        assertFalse(uri.contains("&tn="))
        assertFalse(uri.contains("&tr="))
    }

    // 8. Intent generation with PhonePe target package
    @Test
    fun test08_phonePeIntentGeneration() {
        val intent = UpiIntentBuilder.buildPhoneNumberUpiIntent(
            phoneNumber = "+91 98765 43210",
            receiverName = "Rohan",
            amountPaise = 25000L,
            targetPackage = UpiIntentBuilder.PHONEPE_PACKAGE
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(UpiIntentBuilder.PHONEPE_PACKAGE, intent.`package`)
        assertNotNull(intent.data)
        assertTrue(intent.dataString?.contains("pa=9876543210@ybl") == true)
        assertTrue(intent.dataString?.contains("am=250.00") == true)
    }

    // 9. Intent generation with BHIM target package
    @Test
    fun test09_bhimIntentGeneration() {
        val intent = UpiIntentBuilder.buildPhoneNumberUpiIntent(
            phoneNumber = "9876543210",
            receiverName = "Priya",
            amountPaise = 10000L,
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(UpiIntentBuilder.BHIM_PACKAGE, intent.`package`)
        assertNotNull(intent.data)
        assertTrue(intent.dataString?.contains("pa=9876543210@upi") == true)
        assertTrue(intent.dataString?.contains("am=100.00") == true)
    }

    // 10. Settlement initiation via Phone Number for PhonePe
    @Test
    fun test10_settlementInitiationViaPhonePe() = runBlocking {
        val fId = friendRepository.addFriend(Friend(name = "Aman Gupta", phoneNumber = "9876543210"))
        val friend = friendRepository.getFriendById(fId)!!

        val (settlementId, intent) = upiSettlementManager.initiateSettlement(
            friend = friend,
            amountPaise = 75000L,
            targetPackage = UpiIntentBuilder.PHONEPE_PACKAGE,
            phoneNumber = "9876543210"
        )

        assertTrue(settlementId > 0L)
        val recorded = settlementRepository.getSettlementById(settlementId)
        assertNotNull(recorded)
        assertEquals(75000L, recorded?.amountPaise)
        assertEquals("9876543210@ybl", recorded?.upiReceiverVpa)
        assertEquals(SettlementPaymentStatus.INITIATED, recorded?.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.PENDING, recorded?.upiVerificationStatus)

        // Verifies the generated intent contains the correct URI with amount
        assertEquals(UpiIntentBuilder.PHONEPE_PACKAGE, intent.`package`)
        assertTrue(intent.dataString?.contains("am=750.00") == true)
    }

    // 11. Settlement initiation via Phone Number for BHIM
    @Test
    fun test11_settlementInitiationViaBhim() = runBlocking {
        val fId = friendRepository.addFriend(Friend(name = "Sneha Roy", phoneNumber = "9123456780"))
        val friend = friendRepository.getFriendById(fId)!!

        val (settlementId, intent) = upiSettlementManager.initiateSettlement(
            friend = friend,
            amountPaise = 30000L,
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE,
            phoneNumber = "9123456780"
        )

        assertTrue(settlementId > 0L)
        val recorded = settlementRepository.getSettlementById(settlementId)
        assertNotNull(recorded)
        assertEquals(30000L, recorded?.amountPaise)
        assertEquals("9123456780@upi", recorded?.upiReceiverVpa)

        assertEquals(UpiIntentBuilder.BHIM_PACKAGE, intent.`package`)
        assertTrue(intent.dataString?.contains("am=300.00") == true)
    }

    // 12. Direct App Settlement Initiation (when external intent is rejected)
    @Test
    fun test12_directAppSettlementInitiationAndConfirmation() = runBlocking {
        val fId = friendRepository.addFriend(Friend(name = "Karan", phoneNumber = "9876543210"))
        val friend = friendRepository.getFriendById(fId)!!

        val settlementId = upiSettlementManager.initiateDirectAppSettlement(
            friend = friend,
            amountPaise = 50000L,
            targetPackage = UpiIntentBuilder.PHONEPE_PACKAGE,
            phoneNumber = "9876543210"
        )

        assertTrue(settlementId > 0L)
        val recorded = settlementRepository.getSettlementById(settlementId)
        assertNotNull(recorded)
        assertEquals(SettlementPaymentStatus.INITIATED, recorded?.upiPaymentStatus)
        assertFalse(recorded!!.affectsLedger)

        // User manually confirms settlement upon returning from PhonePe
        val confirmed = upiSettlementManager.confirmManuallyUnverified(settlementId)
        assertEquals(SettlementPaymentStatus.SUCCESS, confirmed.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.MANUAL_UNVERIFIED, confirmed.upiVerificationStatus)
        assertTrue(confirmed.affectsLedger)
    }
}
