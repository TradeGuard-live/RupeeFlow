package com.naman.rupeeflow.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated security regression test suite validating RupeeFlow's security hardening:
 * 1. FileProvider path containment (no directory traversal or whole-cache/files exposure).
 * 2. Network Security Config (strict enforcement of cleartext traffic blocking).
 * 3. AndroidManifest security posture (cleartext disabled, backup disabled, no unneeded permissions).
 */
class AppSecurityHardeningTest {

    private fun findProjectFile(relativePaths: List<String>): File {
        for (rel in relativePaths) {
            val f = File(rel)
            if (f.exists()) return f
        }
        val userDir = System.getProperty("user.dir") ?: "."
        for (rel in relativePaths) {
            val f = File(userDir, rel)
            if (f.exists()) return f
        }
        throw AssertionError("Could not find file in paths: $relativePaths")
    }

    @Test
    fun testFilePathsXmlStrictSubdirectoriesOnly() {
        val filePathsXml = findProjectFile(
            listOf(
                "app/src/main/res/xml/file_paths.xml",
                "src/main/res/xml/file_paths.xml"
            )
        )
        val content = filePathsXml.readText()

        // Path containment checks:
        // Must NOT expose root directory of cache or filesDir via path="." or path="/"
        assertFalse(
            "file_paths.xml must never expose root directory using path='.'",
            content.contains("path=\".\"")
        )
        assertFalse(
            "file_paths.xml must never expose root directory using path='/'",
            content.contains("path=\"/\"")
        )
        assertFalse(
            "file_paths.xml must never permit directory traversal tokens '..'",
            content.contains("..")
        )

        // Must strictly use dedicated subdirectories
        assertTrue(
            "file_paths.xml should safely expose only the reports/ subdirectory for PDF sharing",
            content.contains("path=\"reports/\"")
        )
        assertTrue(
            "file_paths.xml should safely expose only the qr_shares/ subdirectory for QR sharing",
            content.contains("path=\"qr_shares/\"")
        )
        assertTrue(
            "file_paths.xml should safely expose only the receipts/ subdirectory for attachments",
            content.contains("path=\"receipts/\"")
        )
    }

    @Test
    fun testNetworkSecurityConfigEnforcesCleartextDisabled() {
        val nscFile = findProjectFile(
            listOf(
                "app/src/main/res/xml/network_security_config.xml",
                "src/main/res/xml/network_security_config.xml"
            )
        )
        val content = nscFile.readText()

        assertTrue(
            "network_security_config.xml must strictly set cleartextTrafficPermitted='false'",
            content.contains("cleartextTrafficPermitted=\"false\"")
        )
        assertTrue(
            "network_security_config.xml must trust system certificates only",
            content.contains("<certificates src=\"system\" />")
        )
    }

    @Test
    fun testAndroidManifestHardeningParameters() {
        val manifestFile = findProjectFile(
            listOf(
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml"
            )
        )
        val content = manifestFile.readText()

        // 1. Cleartext traffic explicitly disabled at application level
        assertTrue(
            "AndroidManifest must declare android:usesCleartextTraffic='false'",
            content.contains("android:usesCleartextTraffic=\"false\"")
        )

        // 2. Network security config attached
        assertTrue(
            "AndroidManifest must declare android:networkSecurityConfig='@xml/network_security_config'",
            content.contains("android:networkSecurityConfig=\"@xml/network_security_config\"")
        )

        // 3. Android Auto-backup disabled to protect financial SQLite & DataStore against cloud leak
        assertTrue(
            "AndroidManifest must declare android:allowBackup='false'",
            content.contains("android:allowBackup=\"false\"")
        )

        // 4. FileProvider must NOT be exported
        assertTrue(
            "FileProvider must have android:exported='false'",
            content.contains("android:exported=\"false\"")
        )

        // 5. Gmail read-only synchronization requires INTERNET permission.
        // Cleartext traffic remains disabled and trust is restricted by Network Security Config.
        assertTrue(
            "Gmail synchronization requires INTERNET permission",
            content.contains("android.permission.INTERNET")
        )
    }
}

