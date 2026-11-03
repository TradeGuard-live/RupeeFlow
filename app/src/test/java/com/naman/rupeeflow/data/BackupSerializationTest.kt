package com.naman.rupeeflow.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupSerializationTest {

    @Test
    fun testJsonBackupStructure() {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "RupeeFlow")
        root.put("exportTimestamp", System.currentTimeMillis())

        val txns = JSONArray()
        val t1 = JSONObject().apply {
            put("id", 1L)
            put("title", "Swiggy Order")
            put("amountPaise", 45000L)
            put("paymentMethod", "UPI")
            put("transactionType", "EXPENSE")
            put("transactionStatus", "ACTIVE")
        }
        txns.put(t1)
        root.put("transactions", txns)

        val jsonString = root.toString()
        assertTrue(jsonString.contains("RupeeFlow"))
        assertTrue(jsonString.contains("Swiggy Order"))
        assertTrue(jsonString.contains("45000"))

        val parsed = JSONObject(jsonString)
        assertEquals("RupeeFlow", parsed.getString("appName"))
        assertEquals(1, parsed.getInt("version"))
        val restoredTxns = parsed.getJSONArray("transactions")
        assertEquals(1, restoredTxns.length())
        assertEquals(45000L, restoredTxns.getJSONObject(0).getLong("amountPaise"))
    }

    @Test
    fun testCsvFormatting() {
        val header = "ID,Date,Time,Title,Type,Category,Amount(INR),PaymentMethod,PaidBy,Status,Merchant,RefNo,Description"
        assertTrue(header.contains("Amount(INR)"))
        assertTrue(header.contains("PaymentMethod"))

        val sampleRow = "1,2026-11-02,08:30 PM,\"Swiggy\",EXPENSE,Food,450.00,UPI,Me,ACTIVE,\"Swiggy\",12345,\"Dinner\""
        val cols = sampleRow.split(",")
        assertTrue(cols.size >= 10)
    }

    @Test
    fun testEncryptedBackupSecurityAndTamperResistance() {
        val plainPayload = JSONObject().apply {
            put("appName", "RupeeFlow")
            put("version", 2)
            put("exportTimestamp", System.currentTimeMillis())
            val txns = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 100L)
                    put("title", "Confidential Salary")
                    put("amountPaise", 15000000L)
                })
            }
            put("transactions", txns)
        }.toString()

        val password = "StrongUserPassword123!"
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

        val keySpec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val secretKeyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainPayload.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject().apply {
            put("appName", "RupeeFlow")
            put("version", 2)
            put("encrypted", true)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", 65536)
            put("salt", java.util.Base64.getEncoder().encodeToString(salt))
            put("iv", java.util.Base64.getEncoder().encodeToString(iv))
            put("ciphertext", java.util.Base64.getEncoder().encodeToString(ciphertext))
            put("exportTimestamp", System.currentTimeMillis())
        }.toString()

        // Decrypt with correct password
        val decKeySpec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val decKeyBytes = secretKeyFactory.generateSecret(decKeySpec).encoded
        val decKey = javax.crypto.spec.SecretKeySpec(decKeyBytes, "AES")

        val decCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(javax.crypto.Cipher.DECRYPT_MODE, decKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        val decrypted = String(decCipher.doFinal(ciphertext), Charsets.UTF_8)

        assertTrue(decrypted.contains("Confidential Salary"))
        assertTrue(decrypted.contains("15000000"))

        // Decrypt with wrong password must throw AEADBadTagException / security exception
        try {
            val wrongKeySpec = javax.crypto.spec.PBEKeySpec("WrongPassword".toCharArray(), salt, 65536, 256)
            val wrongKeyBytes = secretKeyFactory.generateSecret(wrongKeySpec).encoded
            val wrongKey = javax.crypto.spec.SecretKeySpec(wrongKeyBytes, "AES")

            val badCipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            badCipher.init(javax.crypto.Cipher.DECRYPT_MODE, wrongKey, javax.crypto.spec.GCMParameterSpec(128, iv))
            badCipher.doFinal(ciphertext)
            fail("Expected AEADBadTagException on wrong password")
        } catch (_: javax.crypto.AEADBadTagException) {
            // Success
        }
    }
}
