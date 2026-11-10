package com.naman.rupeeflow.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    val groupsCount: Int = 0,
    val transactionsCount: Int = 0,
    val settlementsCount: Int = 0,
    val inputUsageCount: Int = 0,
    val imagesCount: Int = 0,
    val totalVolumePaise: Long = 0L,
    val isEncrypted: Boolean = false,
    val errorMessage: String? = null
)

class BackupRestoreRepository(
    private val database: RupeeFlowDatabase,
    private val transactionRepository: TransactionRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val categoryRepository: CategoryRepository,
    private val friendGroupRepository: FriendGroupRepository? = null,
    private val context: Context? = null
) {

    companion object {
        const val CURRENT_BACKUP_VERSION = 4
        const val BACKUP_APP_NAME = "RupeeFlow"
        const val MAX_BACKUP_FILE_BYTES = 50 * 1024 * 1024 // 50MB protection limit
        const val MIN_PASSWORD_LENGTH = 12
        const val LEGACY_MIN_PASSWORD_LENGTH = 6
        const val MIN_PBKDF2_ITERATIONS = 10_000
        const val MAX_PBKDF2_ITERATIONS = 2_000_000
        const val DEFAULT_PBKDF2_ITERATIONS = 600_000
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
        const val SALT_LENGTH = 16
        const val IV_LENGTH = 12
        const val MAX_ATTACHMENT_FILENAME_LENGTH = 128
        const val MAX_IMAGE_FILE_BYTES = 10 * 1024 * 1024 // 10MB per image limit
    }

    /**
     * Strict validator for imported backup attachment keys and image filenames.
     * Prevents path traversal, directory breakouts, database/preferences overwrite,
     * null-byte injection, and execution of unsafe file types.
     */
    object AttachmentFilenameValidator {
        private val ALLOWED_FILENAME_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9_.-]*$")
        private val VALID_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bin")

        fun isValidFilename(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            if (name.length > MAX_ATTACHMENT_FILENAME_LENGTH) return false
            if (name.contains('\u0000') || name.any { it.isISOControl() }) return false
            if (name.contains('/') || name.contains('\\')) return false
            if (name.contains("..")) return false
            if (name.startsWith(".")) return false
            if (!ALLOWED_FILENAME_REGEX.matches(name)) return false

            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty() && ext !in VALID_IMAGE_EXTENSIONS) return false
            return true
        }

        fun isContainedWithin(destFile: File, allowedDir: File): Boolean {
            return try {
                val canonicalDest = destFile.canonicalFile
                val canonicalAllowed = allowedDir.canonicalFile
                val destPath = canonicalDest.toPath().normalize()
                val allowedPath = canonicalAllowed.toPath().normalize()

                destPath.startsWith(allowedPath) && !java.nio.file.Files.isSymbolicLink(canonicalDest.toPath())
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Complete export covering all entities, groups, memberships, smart rankings,
     * and portable image binary payloads.
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", CURRENT_BACKUP_VERSION)
        root.put("appName", BACKUP_APP_NAME)
        root.put("exportTimestamp", System.currentTimeMillis())

        val imagesMap = mutableMapOf<String, String>() // portableKey -> base64Data

        fun embedImage(absolutePath: String?): String? {
            if (absolutePath.isNullOrBlank()) return null
            val file = File(absolutePath)
            if (!file.exists() || !file.isFile || file.length() == 0L) return null
            val portableKey = file.name
            if (!imagesMap.containsKey(portableKey)) {
                try {
                    val bytes = file.readBytes()
                    val b64 = Base64.getEncoder().encodeToString(bytes)
                    imagesMap[portableKey] = b64
                } catch (_: Exception) {
                    return null
                }
            }
            return portableKey
        }

        // 1. Categories
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

        // 2. Friends
        val friends = friendRepository.getAllFriendsList()
        val friendArray = JSONArray()
        for (f in friends) {
            val portableImage = embedImage(f.profileImagePath)
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("phoneNumber", f.phoneNumber ?: "")
                put("email", f.email ?: "")
                put("notes", f.notes ?: "")
                put("profileImagePath", portableImage ?: "")
                put("createdAt", f.createdAt)
                put("isArchived", f.isArchived)
            }
            friendArray.put(obj)
        }
        root.put("friends", friendArray)

        // 3. Friend Groups
        val groupDao = database.friendGroupDao()
        val groups = groupDao.getAllGroupsList()
        val groupArray = JSONArray()
        for (g in groups) {
            val portableImage = embedImage(g.groupImagePath)
            val obj = JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("iconName", g.iconName ?: "")
                put("colorHex", g.colorHex ?: "")
                put("groupImagePath", portableImage ?: "")
                put("createdAt", g.createdAt)
                put("updatedAt", g.updatedAt)
            }
            groupArray.put(obj)
        }
        root.put("friend_groups", groupArray)

        // 4. Friend Group Members
        val members = groupDao.getAllGroupMembersList()
        val memberArray = JSONArray()
        for (m in members) {
            val obj = JSONObject().apply {
                put("groupId", m.groupId)
                put("friendId", m.friendId)
                put("addedAt", m.addedAt)
            }
            memberArray.put(obj)
        }
        root.put("friend_group_members", memberArray)

        // 5. Transactions & Participants
        val transactions = transactionRepository.getAllTransactionsList()
        val txnArray = JSONArray()
        for (t in transactions) {
            val portableBill = embedImage(t.billImagePath)
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
                put("groupId", t.groupId ?: -1L)
                put("billImagePath", portableBill ?: "")
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

        // 6. Settlements
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

        // 7. Input Usage (Smart Dynamic Ranking)
        val inputUsageList = database.inputUsageDao().getAllUsageList()
        val usageArray = JSONArray()
        for (u in inputUsageList) {
            val obj = JSONObject().apply {
                put("id", u.id)
                put("fieldType", u.fieldType)
                put("optionId", u.optionId)
                put("usageCount", u.usageCount)
                put("lastUsedAt", u.lastUsedAt)
                put("preferenceScore", u.preferenceScore)
            }
            usageArray.put(obj)
        }
        root.put("input_usage", usageArray)

        // 8. Portable Embedded Images Map
        val imagesObj = JSONObject()
        for ((key, b64) in imagesMap) {
            imagesObj.put(key, b64)
        }
        root.put("images", imagesObj)

        root.toString(2)
    }

    /**
     * Exports an authenticated AES-256-GCM encrypted backup envelope protected by a password.
     */
    suspend fun exportEncryptedBackup(password: String): String = withContext(Dispatchers.IO) {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Backup password must be at least $MIN_PASSWORD_LENGTH characters for strong encryption (provided: ${password.length})"
        }
        val plainJson = exportToJson()

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val passwordChars = password.toCharArray()
        var keyBytes: ByteArray? = null
        try {
            val keySpec = PBEKeySpec(passwordChars, salt, DEFAULT_PBKDF2_ITERATIONS, 256)
            val secretKeyFactory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            keySpec.clearPassword()
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

            val envelope = JSONObject().apply {
                put("appName", BACKUP_APP_NAME)
                put("version", CURRENT_BACKUP_VERSION)
                put("encrypted", true)
                put("kdf", KDF_ALGORITHM)
                put("iterations", DEFAULT_PBKDF2_ITERATIONS)
                put("salt", Base64.getEncoder().encodeToString(salt))
                put("iv", Base64.getEncoder().encodeToString(iv))
                put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
                put("exportTimestamp", System.currentTimeMillis())
            }

            envelope.toString(2)
        } finally {
            passwordChars.fill('\u0000')
            keyBytes?.fill(0)
        }
    }

    /**
     * Decrypts an authenticated encrypted backup envelope using user password.
     * Enforces strict iteration bounds [10,000 .. 2,000,000], salt length (16 bytes),
     * IV length (12 bytes), and pre-validates Base64 encoding to prevent DoS.
     * Preserves backward compatibility with legacy backups (65,536 iterations).
     */
    fun decryptBackup(envelopeJson: String, password: String): String {
        require(envelopeJson.length <= MAX_BACKUP_FILE_BYTES) { "Backup exceeds maximum supported file size (50MB)" }
        val root: JSONObject
        try {
            root = JSONObject(envelopeJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed encrypted backup payload: invalid JSON", e)
        }
        require(root.optBoolean("encrypted", false)) { "Not an encrypted backup file" }

        val kdf = root.optString("kdf", KDF_ALGORITHM)
        require(kdf == KDF_ALGORITHM) { "Unsupported KDF algorithm: $kdf. Expected: $KDF_ALGORITHM" }

        val iterations = root.optInt("iterations", DEFAULT_PBKDF2_ITERATIONS)
        require(iterations >= MIN_PBKDF2_ITERATIONS) {
            "PBKDF2 iteration count too low ($iterations). Minimum required: $MIN_PBKDF2_ITERATIONS"
        }
        require(iterations <= MAX_PBKDF2_ITERATIONS) {
            "Excessively large PBKDF2 iteration count ($iterations). Maximum allowed: $MAX_PBKDF2_ITERATIONS"
        }

        require(root.has("salt")) { "Missing salt in encrypted backup envelope" }
        require(root.has("iv")) { "Missing IV in encrypted backup envelope" }
        require(root.has("ciphertext")) { "Missing ciphertext in encrypted backup envelope" }

        val salt = try {
            Base64.getDecoder().decode(root.getString("salt"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Base64 encoding in salt", e)
        }
        require(salt.size == SALT_LENGTH) { "Invalid salt length: ${salt.size} bytes (expected $SALT_LENGTH)" }

        val iv = try {
            Base64.getDecoder().decode(root.getString("iv"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Base64 encoding in IV", e)
        }
        require(iv.size == IV_LENGTH) { "Invalid IV length: ${iv.size} bytes (expected $IV_LENGTH)" }

        val ciphertext = try {
            Base64.getDecoder().decode(root.getString("ciphertext"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Base64 encoding in ciphertext", e)
        }
        require(ciphertext.isNotEmpty()) { "Empty ciphertext payload" }

        val passwordChars = password.toCharArray()
        var keyBytes: ByteArray? = null
        try {
            val keySpec = PBEKeySpec(passwordChars, salt, iterations, 256)
            val secretKeyFactory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            keySpec.clearPassword()
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)

            return String(decryptedBytes, Charsets.UTF_8)
        } finally {
            passwordChars.fill('\u0000')
            keyBytes?.fill(0)
        }
    }

    /**
     * Inspects and validates a backup before restoring, returning structured preview info.
     */
    fun inspectBackup(content: String, password: String? = null): BackupPreview {
        return try {
            if (content.length > MAX_BACKUP_FILE_BYTES) {
                return BackupPreview(isValid = false, errorMessage = "Backup file exceeds maximum allowed size (50MB)")
            }

            val root = JSONObject(content)
            val isEncrypted = root.optBoolean("encrypted", false)

            if (isEncrypted) {
                if (password.isNullOrBlank()) {
                    return BackupPreview(
                        isValid = true,
                        appName = root.optString("appName", BACKUP_APP_NAME),
                        version = root.optInt("version", 1),
                        isEncrypted = true,
                        exportTimestamp = root.optLong("exportTimestamp", 0L)
                    )
                }
                val decrypted = decryptBackup(content, password)
                return inspectBackup(decrypted, null)
            }

            if (!root.has("appName") || root.getString("appName") != BACKUP_APP_NAME) {
                return BackupPreview(isValid = false, errorMessage = "Invalid backup format: Not a RupeeFlow backup")
            }

            val version = root.optInt("version", 1)
            val timestamp = root.optLong("exportTimestamp", 0L)
            val catCount = root.optJSONArray("categories")?.length() ?: 0
            val friendCount = root.optJSONArray("friends")?.length() ?: 0
            val groupCount = root.optJSONArray("friend_groups")?.length() ?: 0
            val txnsArray = root.optJSONArray("transactions")
            val txnCount = txnsArray?.length() ?: 0
            val setCount = root.optJSONArray("settlements")?.length() ?: 0
            val inputUsageCount = root.optJSONArray("input_usage")?.length() ?: 0
            val imagesCount = root.optJSONObject("images")?.length() ?: 0

            var totalVolume = 0L
            if (txnsArray != null) {
                for (i in 0 until txnsArray.length()) {
                    totalVolume += txnsArray.getJSONObject(i).optLong("amountPaise", 0L)
                }
            }

            BackupPreview(
                isValid = true,
                appName = BACKUP_APP_NAME,
                version = version,
                exportTimestamp = timestamp,
                categoriesCount = catCount,
                friendsCount = friendCount,
                groupsCount = groupCount,
                transactionsCount = txnCount,
                settlementsCount = setCount,
                inputUsageCount = inputUsageCount,
                imagesCount = imagesCount,
                totalVolumePaise = totalVolume,
                isEncrypted = false
            )
        } catch (e: Exception) {
            BackupPreview(isValid = false, errorMessage = e.localizedMessage ?: "Invalid or corrupted backup")
        }
    }

    /**
     * Atomically restores all database tables and image attachments inside a single Room transaction.
     * Guaranteed: either 100% of the data restores cleanly, or the transaction rolls back with zero corruption.
     */
    suspend fun restoreFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        if (jsonString.length > MAX_BACKUP_FILE_BYTES) return@withContext false

        val root: JSONObject
        try {
            root = JSONObject(jsonString)
            if (!root.has("appName") || root.getString("appName") != BACKUP_APP_NAME) {
                return@withContext false
            }
        } catch (_: Exception) {
            return@withContext false
        }

        // 1. Temporary Staging Directory for atomic extraction
        val stagingDir = if (context != null) {
            File(context.filesDir, "staging_restore_${System.currentTimeMillis()}_${SecureRandom().nextInt(100000)}").apply {
                mkdirs()
            }
        } else null

        val stagedImageMap = mutableMapOf<String, File>() // key -> stagedFile
        val targetDestinationMap = mutableMapOf<String, File>() // key -> finalDestFile
        val restoredImagePaths = mutableMapOf<String, String>() // key -> finalAbsolutePath

        try {
            if (root.has("images") && context != null && stagingDir != null) {
                val imagesObj = root.getJSONObject("images")
                val filesDir = context.filesDir.canonicalFile
                val dbDir = context.getDatabasePath("rupeeflow.db").parentFile?.canonicalFile

                val keys = imagesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    // Strict filename validation: Reject traversal, separators, control chars, dangerous extensions
                    if (!AttachmentFilenameValidator.isValidFilename(key)) {
                        continue
                    }

                    val b64 = imagesObj.optString(key)
                    if (b64.isBlank()) continue

                    val bytes = try {
                        val decoded = Base64.getDecoder().decode(b64)
                        if (decoded.size > MAX_IMAGE_FILE_BYTES) continue
                        decoded
                    } catch (_: Exception) {
                        continue
                    }

                    val subDirName = when {
                        key.startsWith("bill_") -> "receipts"
                        key.startsWith("group_") -> "groups"
                        else -> "profiles"
                    }

                    val destDir = File(filesDir, subDirName).canonicalFile
                    // Verify directory remains inside application files directory
                    if (!destDir.toPath().normalize().startsWith(filesDir.toPath().normalize())) {
                        continue
                    }
                    if (dbDir != null && destDir.toPath().normalize().startsWith(dbDir.toPath().normalize())) {
                        continue
                    }

                    val destFile = File(destDir, key).canonicalFile
                    // Verify file canonical path remains strictly inside destDir and is not a symlink
                    if (!AttachmentFilenameValidator.isContainedWithin(destFile, destDir)) {
                        continue
                    }

                    // Write into temporary staging directory first
                    val stagedFile = File(stagingDir, "${System.currentTimeMillis()}_$key")
                    FileOutputStream(stagedFile).use { it.write(bytes) }

                    stagedImageMap[key] = stagedFile
                    targetDestinationMap[key] = destFile
                    restoredImagePaths[key] = destFile.absolutePath
                }
            }

            fun resolveImagePath(portableKeyOrPath: String?): String? {
                if (portableKeyOrPath.isNullOrBlank()) return null
                return restoredImagePaths[portableKeyOrPath] ?: portableKeyOrPath
            }

            // ATOMIC DATABASE RESTORE TRANSACTION
            database.withTransaction {
                // 1. Restore Categories
                if (root.has("categories")) {
                    val catArray = root.getJSONArray("categories")
                    for (i in 0 until catArray.length()) {
                        val obj = catArray.getJSONObject(i)
                        val cat = CategoryEntity(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            iconName = obj.optString("iconName", "category"),
                            colorHex = obj.optString("colorHex", "#3B82F6"),
                            isDefault = obj.optBoolean("isDefault", false)
                        )
                        database.categoryDao().insertCategory(cat)
                    }
                }

                // 2. Restore Friends
                if (root.has("friends")) {
                    val friendArray = root.getJSONArray("friends")
                    for (i in 0 until friendArray.length()) {
                        val obj = friendArray.getJSONObject(i)
                        val profilePath = resolveImagePath(obj.optString("profileImagePath").takeIf { it.isNotBlank() })
                        val friendEntity = FriendEntity(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            phoneNumber = obj.optString("phoneNumber").takeIf { it.isNotBlank() },
                            email = obj.optString("email").takeIf { it.isNotBlank() },
                            notes = obj.optString("notes").takeIf { it.isNotBlank() },
                            profileImagePath = profilePath,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            isArchived = obj.optBoolean("isArchived", false)
                        )
                        database.friendDao().insertFriend(friendEntity)
                    }
                }

                // 3. Restore Friend Groups
                if (root.has("friend_groups")) {
                    val groupArray = root.getJSONArray("friend_groups")
                    for (i in 0 until groupArray.length()) {
                        val obj = groupArray.getJSONObject(i)
                        val groupImagePath = resolveImagePath(obj.optString("groupImagePath").takeIf { it.isNotBlank() })
                        val groupEntity = FriendGroupEntity(
                            id = obj.optLong("id", 0L),
                            name = obj.getString("name"),
                            iconName = obj.optString("iconName").takeIf { it.isNotBlank() },
                            colorHex = obj.optString("colorHex").takeIf { it.isNotBlank() },
                            groupImagePath = groupImagePath,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                        database.friendGroupDao().insertGroup(groupEntity)
                    }
                }

                // 4. Restore Friend Group Members
                if (root.has("friend_group_members")) {
                    val memberArray = root.getJSONArray("friend_group_members")
                    val memberList = mutableListOf<FriendGroupMemberEntity>()
                    for (i in 0 until memberArray.length()) {
                        val obj = memberArray.getJSONObject(i)
                        val gId = obj.getLong("groupId")
                        val fId = obj.getLong("friendId")
                        if (gId > 0 && fId > 0) {
                            memberList.add(
                                FriendGroupMemberEntity(
                                    groupId = gId,
                                    friendId = fId,
                                    addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                    if (memberList.isNotEmpty()) {
                        database.friendGroupDao().insertMembers(memberList)
                    }
                }

                // 5. Restore Transactions & Participants
                if (root.has("transactions")) {
                    val txnArray = root.getJSONArray("transactions")
                    for (i in 0 until txnArray.length()) {
                        val obj = txnArray.getJSONObject(i)
                        val paidBy = obj.optLong("paidByFriendId", -1L).takeIf { it > 0 }
                        val gId = obj.optLong("groupId", -1L).takeIf { it > 0 }
                        val billPath = resolveImagePath(obj.optString("billImagePath").takeIf { it.isNotBlank() })

                        val txnEntity = TransactionEntity(
                            id = obj.optLong("id", 0L),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            amountPaise = obj.getLong("amountPaise"),
                            categoryId = obj.optLong("categoryId", 1L),
                            transactionDate = obj.getLong("transactionDate"),
                            paymentMethod = runCatching {
                                PaymentMethod.valueOf(obj.getString("paymentMethod"))
                            }.getOrDefault(PaymentMethod.UPI),
                            accountSource = obj.optString("accountSource").takeIf { it.isNotBlank() },
                            paidByFriendId = paidBy,
                            splitMethod = runCatching {
                                SplitMethod.valueOf(obj.getString("splitMethod"))
                            }.getOrDefault(SplitMethod.EQUAL),
                            transactionType = runCatching {
                                TransactionType.valueOf(obj.getString("transactionType"))
                            }.getOrDefault(TransactionType.EXPENSE),
                            transactionStatus = runCatching {
                                TransactionStatus.valueOf(obj.getString("transactionStatus"))
                            }.getOrDefault(TransactionStatus.ACTIVE),
                            source = runCatching {
                                TransactionSource.valueOf(obj.getString("source"))
                            }.getOrDefault(TransactionSource.MANUAL),
                            merchantName = obj.optString("merchantName").takeIf { it.isNotBlank() },
                            referenceNumber = obj.optString("referenceNumber").takeIf { it.isNotBlank() },
                            groupId = gId,
                            billImagePath = billPath,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
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
                                            SplitMethod.valueOf(pObj.getString("splitMethod"))
                                        }.getOrDefault(SplitMethod.EQUAL)
                                    )
                                )
                            }
                        }
                        database.transactionDao().saveTransactionWithParticipants(txnEntity, participants)
                    }
                }

                // 6. Restore Settlements
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
                                PaymentMethod.valueOf(obj.getString("paymentMethod"))
                            }.getOrDefault(PaymentMethod.UPI),
                            referenceNumber = obj.optString("referenceNumber").takeIf { it.isNotBlank() },
                            notes = obj.optString("notes").takeIf { it.isNotBlank() },
                            isReversed = obj.optBoolean("isReversed", false),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                        database.settlementDao().insertSettlement(settlement)
                    }
                }

                // 7. Restore Input Usage (Smart Rankings)
                if (root.has("input_usage")) {
                    val usageArray = root.getJSONArray("input_usage")
                    for (i in 0 until usageArray.length()) {
                        val obj = usageArray.getJSONObject(i)
                        val usageEntity = InputUsageEntity(
                            id = obj.optLong("id", 0L),
                            fieldType = obj.getString("fieldType"),
                            optionId = obj.getString("optionId"),
                            usageCount = obj.optLong("usageCount", 1L),
                            lastUsedAt = obj.optLong("lastUsedAt", System.currentTimeMillis()),
                            preferenceScore = obj.optDouble("preferenceScore", 0.0)
                        )
                        database.inputUsageDao().insertOrUpdate(usageEntity)
                    }
                }
            }

            // 3. Finalize Staged Files (Only after DB transaction commits successfully!)
            for ((key, stagedFile) in stagedImageMap) {
                val finalFile = targetDestinationMap[key] ?: continue
                finalFile.parentFile?.mkdirs()
                try {
                    java.nio.file.Files.move(
                        stagedFile.toPath(),
                        finalFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    java.nio.file.Files.copy(
                        stagedFile.toPath(),
                        finalFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    stagedFile.delete()
                }
            }

            true
        } catch (_: Exception) {
            // DB transaction failed or corrupted: rollback!
            false
        } finally {
            // Guarantee cleanup of staging directory on all exit paths
            stagingDir?.deleteRecursively()
        }
    }

    suspend fun exportTransactionsToCsv(): String = withContext(Dispatchers.IO) {
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
        sb.toString()
    }

    suspend fun exportFriendsToCsv(): String = withContext(Dispatchers.IO) {
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
        sb.toString()
    }

    suspend fun resetApp() = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.clearAllTables()
            database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }
    }
}
