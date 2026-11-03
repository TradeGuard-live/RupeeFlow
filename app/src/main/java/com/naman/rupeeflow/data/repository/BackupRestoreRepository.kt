package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPreview(
    val isValid: Boolean,
    val appName: String = "",
    val version: Int = 1,
    val exportTimestamp: Long = 0L,
    val categoriesCount: Int = 0,
    val friendsCount: Int = 0,
    val transactionsCount: Int = 0,
    val settlementsCount: Int = 0,
    val totalVolumePaise: Long = 0L,
    val isEncrypted: Boolean = false,
    val errorMessage: String? = null
)

class BackupRestoreRepository(
    private val database: RupeeFlowDatabase,
    private val transactionRepository: TransactionRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val categoryRepository: CategoryRepository
) {

    suspend fun exportToJson(): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("appName", "RupeeFlow")
        root.put("exportTimestamp", System.currentTimeMillis())

        // Categories
        val categories = categoryRepository.getAllCategoriesList()
        val catArray = JSONArray()
        for (c in categories) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("iconName", c.iconName)
                put("colorHex", c.colorHex)
                put("isDefault", c.isDefault)
            }
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // Friends
        val friends = friendRepository.getAllFriendsList()
        val friendArray = JSONArray()
        for (f in friends) {
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("phoneNumber", f.phoneNumber ?: "")
                put("email", f.email ?: "")
                put("notes", f.notes ?: "")
                put("createdAt", f.createdAt)
                put("isArchived", f.isArchived)
            }
            friendArray.put(obj)
        }
        root.put("friends", friendArray)

        // Transactions & Participants
        val transactions = transactionRepository.getAllTransactionsList()
        val txnArray = JSONArray()
        for (t in transactions) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("description", t.description)
                put("amountPaise", t.amountPaise)
                put("categoryId", t.categoryId)
                put("transactionDate", t.transactionDate)
                put("paymentMethod", t.paymentMethod.name)
                put("accountSource", t.accountSource ?: "")
                put("paidByFriendId", t.paidByFriendId ?: -1L)
                put("splitMethod", t.splitMethod.name)
                put("transactionType", t.transactionType.name)
                put("transactionStatus", t.transactionStatus.name)
                put("source", t.source.name)
                put("merchantName", t.merchantName ?: "")
                put("referenceNumber", t.referenceNumber ?: "")
                put("createdAt", t.createdAt)
            }

            val partArray = JSONArray()
            for (p in t.participants) {
                val pObj = JSONObject().apply {
                    put("id", p.id)
                    put("friendId", p.friendId ?: -1L)
                    put("shareAmountPaise", p.shareAmountPaise)
                    put("percentage", p.percentage)
                    put("splitMethod", p.splitMethod.name)
                }
                partArray.put(pObj)
            }
            obj.put("participants", partArray)
            txnArray.put(obj)
        }
        root.put("transactions", txnArray)

        // Settlements
        val settlements = settlementRepository.getAllSettlementsList()
        val setArray = JSONArray()
        for (s in settlements) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("payerId", s.payerId ?: -1L)
                put("receiverId", s.receiverId ?: -1L)
                put("amountPaise", s.amountPaise)
                put("settlementDate", s.settlementDate)
                put("paymentMethod", s.paymentMethod.name)
                put("referenceNumber", s.referenceNumber ?: "")
                put("notes", s.notes ?: "")
                put("isReversed", s.isReversed)
                put("createdAt", s.createdAt)
            }
            setArray.put(obj)
        }
        root.put("settlements", setArray)

        return root.toString(2)
    }

    /**
     * ✅ Exports an AES-256-GCM encrypted backup protected by a user password.
     */
    suspend fun exportEncryptedBackup(password: String): String {
        require(password.length >= 4) { "Password must be at least 4 characters" }
        val plainJson = exportToJson()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val keySpec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject().apply {
            put("appName", "RupeeFlow")
            put("version", 2)
            put("encrypted", true)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", 65536)
            put("salt", Base64.getEncoder().encodeToString(salt))
            put("iv", Base64.getEncoder().encodeToString(iv))
            put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
            put("exportTimestamp", System.currentTimeMillis())
        }

        return envelope.toString(2)
    }

    /**
     * ✅ Decrypts an encrypted backup envelope using password.
     */
    fun decryptBackup(envelopeJson: String, password: String): String {
        val root = JSONObject(envelopeJson)
        require(root.optBoolean("encrypted", false)) { "Not an encrypted backup file" }

        val salt = Base64.getDecoder().decode(root.getString("salt"))
        val iv = Base64.getDecoder().decode(root.getString("iv"))
        val ciphertext = Base64.getDecoder().decode(root.getString("ciphertext"))
        val iterations = root.optInt("iterations", 65536)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(ciphertext)

        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * ✅ Inspects and validates a backup before restoring, returning structured preview info.
     */
    fun inspectBackup(content: String, password: String? = null): BackupPreview {
        return try {
            val root = JSONObject(content)
            val isEncrypted = root.optBoolean("encrypted", false)

            if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    return BackupPreview(
                        isValid = true,
                        appName = root.optString("appName", "RupeeFlow"),
                        isEncrypted = true,
                        exportTimestamp = root.optLong("exportTimestamp", 0L)
                    )
                }
                val decrypted = decryptBackup(content, password)
                return inspectBackup(decrypted, null)
            }

            if (!root.has("appName") || root.getString("appName") != "RupeeFlow") {
                return BackupPreview(isValid = false, errorMessage = "Invalid backup format: Not a RupeeFlow backup")
            }

            val version = root.optInt("version", 1)
            val timestamp = root.optLong("exportTimestamp", 0L)
            val catCount = root.optJSONArray("categories")?.length() ?: 0
            val friendCount = root.optJSONArray("friends")?.length() ?: 0
            val txnsArray = root.optJSONArray("transactions")
            val txnCount = txnsArray?.length() ?: 0
            val setCount = root.optJSONArray("settlements")?.length() ?: 0

            var totalVolume = 0L
            if (txnsArray != null) {
                for (i in 0 until txnsArray.length()) {
                    totalVolume += txnsArray.getJSONObject(i).optLong("amountPaise", 0L)
                }
            }

            BackupPreview(
                isValid = true,
                appName = "RupeeFlow",
                version = version,
                exportTimestamp = timestamp,
                categoriesCount = catCount,
                friendsCount = friendCount,
                transactionsCount = txnCount,
                settlementsCount = setCount,
                totalVolumePaise = totalVolume,
                isEncrypted = false
            )
        } catch (e: Exception) {
            BackupPreview(isValid = false, errorMessage = e.localizedMessage ?: "Invalid or corrupted backup")
        }
    }

    suspend fun restoreFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("appName") || root.getString("appName") != "RupeeFlow") {
                return false
            }

            // Restore Friends
            if (root.has("friends")) {
                val friendArray = root.getJSONArray("friends")
                for (i in 0 until friendArray.length()) {
                    val obj = friendArray.getJSONObject(i)
                    val friendEntity = FriendEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        phoneNumber = obj.optString("phoneNumber").takeIf { it.isNotBlank() },
                        email = obj.optString("email").takeIf { it.isNotBlank() },
                        notes = obj.optString("notes").takeIf { it.isNotBlank() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isArchived = obj.optBoolean("isArchived", false)
                    )
                    database.friendDao().insertFriend(friendEntity)
                }
            }

            // Restore Transactions
            if (root.has("transactions")) {
                val txnArray = root.getJSONArray("transactions")
                for (i in 0 until txnArray.length()) {
                    val obj = txnArray.getJSONObject(i)
                    val paidBy = obj.optLong("paidByFriendId", -1L).takeIf { it > 0 }
                    val txnEntity = TransactionEntity(
                        id = obj.optLong("id", 0L),
                        title = obj.getString("title"),
                        description = obj.optString("description", ""),
                        amountPaise = obj.getLong("amountPaise"),
                        categoryId = obj.getLong("categoryId"),
                        transactionDate = obj.getLong("transactionDate"),
                        paymentMethod = runCatching {
                            com.naman.rupeeflow.domain.model.PaymentMethod.valueOf(obj.getString("paymentMethod"))
                        }.getOrDefault(com.naman.rupeeflow.domain.model.PaymentMethod.UPI),
                        accountSource = obj.optString("accountSource").takeIf { it.isNotBlank() },
                        paidByFriendId = paidBy,
                        splitMethod = runCatching {
                            com.naman.rupeeflow.domain.model.SplitMethod.valueOf(obj.getString("splitMethod"))
                        }.getOrDefault(com.naman.rupeeflow.domain.model.SplitMethod.EQUAL),
                        transactionType = runCatching {
                            com.naman.rupeeflow.domain.model.TransactionType.valueOf(obj.getString("transactionType"))
                        }.getOrDefault(com.naman.rupeeflow.domain.model.TransactionType.EXPENSE),
                        transactionStatus = runCatching {
                            com.naman.rupeeflow.domain.model.TransactionStatus.valueOf(obj.getString("transactionStatus"))
                        }.getOrDefault(com.naman.rupeeflow.domain.model.TransactionStatus.ACTIVE),
                        source = runCatching {
                            com.naman.rupeeflow.domain.model.TransactionSource.valueOf(obj.getString("source"))
                        }.getOrDefault(com.naman.rupeeflow.domain.model.TransactionSource.MANUAL),
                        merchantName = obj.optString("merchantName").takeIf { it.isNotBlank() },
                        referenceNumber = obj.optString("referenceNumber").takeIf { it.isNotBlank() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )

                    val participants = mutableListOf<TransactionParticipantEntity>()
                    if (obj.has("participants")) {
                        val pArray = obj.getJSONArray("participants")
                        for (j in 0 until pArray.length()) {
                            val pObj = pArray.getJSONObject(j)
                            val fId = pObj.optLong("friendId", -1L).takeIf { it > 0 }
                            participants.add(
                                TransactionParticipantEntity(
                                    id = pObj.optLong("id", 0L),
                                    transactionId = txnEntity.id,
                                    friendId = fId,
                                    shareAmountPaise = pObj.getLong("shareAmountPaise"),
                                    percentage = pObj.optDouble("percentage", 0.0),
                                    splitMethod = runCatching {
                                        com.naman.rupeeflow.domain.model.SplitMethod.valueOf(pObj.getString("splitMethod"))
                                    }.getOrDefault(com.naman.rupeeflow.domain.model.SplitMethod.EQUAL)
                                )
                            )
                        }
                    }
                    database.transactionDao().saveTransactionWithParticipants(txnEntity, participants)
                }
            }

            // Restore Settlements
            if (root.has("settlements")) {
                val setArray = root.getJSONArray("settlements")
                for (i in 0 until setArray.length()) {
                    val obj = setArray.getJSONObject(i)
                    val payerId = obj.optLong("payerId", -1L).takeIf { it > 0 }
                    val receiverId = obj.optLong("receiverId", -1L).takeIf { it > 0 }
                    val settlement = SettlementEntity(
                        id = obj.optLong("id", 0L),
                        payerId = payerId,
                        receiverId = receiverId,
                        amountPaise = obj.getLong("amountPaise"),
                        settlementDate = obj.getLong("settlementDate"),
                        paymentMethod = runCatching {
                            com.naman.rupeeflow.domain.model.PaymentMethod.valueOf(obj.getString("paymentMethod"))
                        }.getOrDefault(com.naman.rupeeflow.domain.model.PaymentMethod.UPI),
                        referenceNumber = obj.optString("referenceNumber").takeIf { it.isNotBlank() },
                        notes = obj.optString("notes").takeIf { it.isNotBlank() },
                        isReversed = obj.optBoolean("isReversed", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    database.settlementDao().insertSettlement(settlement)
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun exportTransactionsToCsv(): String {
        val txns = transactionRepository.getAllTransactionsList()
        val sb = StringBuilder()
        sb.append("ID,Date,Time,Title,Type,Category,Amount(INR),PaymentMethod,PaidBy,Status,Merchant,RefNo,Description\n")

        for (t in txns) {
            val dateStr = DateTimeUtils.formatDate(t.transactionDate, "yyyy-MM-dd")
            val timeStr = DateTimeUtils.formatTime(t.transactionDate)
            val amtStr = CurrencyUtils.formatPaiseToRupees(t.amountPaise, includeSymbol = false)
            val catStr = t.category?.name ?: "Other"
            val titleEscaped = "\"${t.title.replace("\"", "\"\"")}\""
            val descEscaped = "\"${t.description.replace("\"", "\"\"")}\""
            val merchantEscaped = "\"${(t.merchantName ?: "").replace("\"", "\"\"")}\""

            sb.append("${t.id},$dateStr,$timeStr,$titleEscaped,${t.transactionType.name},$catStr,$amtStr,${t.paymentMethod.name},${t.paidByName},${t.transactionStatus.name},$merchantEscaped,${t.referenceNumber ?: ""},$descEscaped\n")
        }
        return sb.toString()
    }

    suspend fun exportFriendsToCsv(): String {
        val rawFriends = friendRepository.getAllFriendsList()
        val txns = transactionRepository.getAllTransactionsList()
        val sets = settlementRepository.getAllSettlementsList()
        val friendsWithLedger = LedgerCalculator.computeFriendLedgers(rawFriends, txns, sets)

        val sb = StringBuilder()
        sb.append("FriendID,Name,Phone,Email,TheyOweMe(INR),IOweThem(INR),NetBalance(INR),Status,Notes\n")

        for (f in friendsWithLedger) {
            val theyOweStr = CurrencyUtils.formatPaiseToRupees(f.totalTheyOweMePaise, includeSymbol = false)
            val iOweStr = CurrencyUtils.formatPaiseToRupees(f.totalIOweThemPaise, includeSymbol = false)
            val netStr = CurrencyUtils.formatPaiseToRupees(f.netBalancePaise, includeSymbol = false)
            val nameEscaped = "\"${f.name.replace("\"", "\"\"")}\""
            val notesEscaped = "\"${(f.notes ?: "").replace("\"", "\"\"")}\""

            sb.append("${f.id},$nameEscaped,${f.phoneNumber ?: ""},${f.email ?: ""},$theyOweStr,$iOweStr,$netStr,${f.status.name},$notesEscaped\n")
        }
        return sb.toString()
    }

    suspend fun resetApp() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
            database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }
    }
}
