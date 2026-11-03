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
}
