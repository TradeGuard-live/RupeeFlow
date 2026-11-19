package com.naman.rupeeflow.upi

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.random.Random

/**
 * Handles local generation of standard NPCI UPI payment request URIs and QR code bitmaps.
 *
 * All processing is performed strictly on-device without network transmission of financial details.
 */
object UpiQrCodeGenerator {

    /**
     * Constructs the standard NPCI UPI payment request URI string for personal settlements.
     *
     * Format:
     * upi://pay?pa=RECEIVER_UPI_ID&pn=RECEIVER_NAME&am=9.30&cu=INR&tn=RupeeFlow%20Settlement&tr=UNIQUE_REFERENCE
     *
     * Rules:
     * 1. 'pa' retains literal '@' and lowercase VPA.
     * 2. 'pn' is URL-encoded.
     * 3. 'am' is formatted with exactly 2 decimal places.
     * 4. 'cu' is set to 'INR'.
     * 5. 'tn' is URL-encoded.
     * 6. 'tr' contains unique transaction reference where supported.
     * 7. 'mam' (minimum amount) is strictly omitted for standard fixed amount payment requests.
     */
    fun buildSettlementQrUri(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String = "RupeeFlow Settlement",
        transactionRef: String = generateQrReference()
    ): String {
        require(UpiValidator.isValidUpiId(receiverUpiId)) { "Invalid receiver UPI ID: $receiverUpiId" }
        require(amountPaise > 0L) { "Amount must be greater than zero" }

        val amountRupees = amountPaise.toDouble() / 100.0
        val formattedAmount = String.format(Locale.US, "%.2f", amountRupees)

        val cleanUpi = receiverUpiId.trim().lowercase()
        val cleanPn = receiverName.trim()
            .replace(Regex("""[^a-zA-Z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(50)
            .ifBlank { "RupeeFlow User" }

        val cleanTn = note.trim()
            .replace(Regex("""[^a-zA-Z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(50)
            .ifBlank { "RupeeFlow Settlement" }

        val cleanTr = transactionRef.trim()
            .replace(Regex("""[^a-zA-Z0-9]"""), "")
            .take(35)

        val sb = StringBuilder("upi://pay?")
            .append("pa=").append(cleanUpi)
            .append("&pn=").append(Uri.encode(cleanPn))
            .append("&am=").append(formattedAmount)
            .append("&cu=INR")

        if (cleanTn.isNotBlank()) {
            sb.append("&tn=").append(Uri.encode(cleanTn))
        }

        if (cleanTr.isNotBlank()) {
            sb.append("&tr=").append(Uri.encode(cleanTr))
        }

        return sb.toString()
    }

    /**
     * Generates a square QR code Bitmap locally using ZXing QRCodeWriter.
     *
     * @param content The URI or payload to encode into the QR code.
     * @param sizePixels The width and height of the resulting bitmap.
     * @return Android Bitmap of the generated QR code.
     */
    fun generateQrBitmap(
        content: String,
        sizePixels: Int = 512
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )

        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePixels,
            sizePixels,
            hints
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Generates a collision-resistant unique transaction reference for QR requests.
     */
    fun generateQrReference(): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = Random.nextInt(1000, 9999)
        return "RFQR${timestamp}${randomSuffix}"
    }

    /**
     * Saves the QR bitmap to the device's public Pictures/RupeeFlow directory.
     *
     * @return Uri of the saved file, or null on failure.
     */
    fun saveQrBitmapToStorage(
        context: Context,
        bitmap: Bitmap,
        reference: String
    ): Uri? {
        val fileName = "RupeeFlow_QR_${reference}.png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RupeeFlow")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
                imageUri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val rupeeFlowDir = File(picturesDir, "RupeeFlow").apply { if (!exists()) mkdirs() }
                val targetFile = File(rupeeFlowDir, fileName)

                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Uri.fromFile(targetFile)
            }
        } catch (_: Exception) {
            // Fallback: save to app-specific external files dir
            try {
                val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "RupeeFlow")
                    .apply { if (!exists()) mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                FileOutputStream(fallbackFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Uri.fromFile(fallbackFile)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Saves the QR bitmap to the application cache and launches the system share chooser.
     */
    fun shareQrCode(
        context: Context,
        bitmap: Bitmap,
        shareText: String,
        reference: String
    ) {
        try {
            val cacheDir = File(context.cacheDir, "qr_shares").apply { if (!exists()) mkdirs() }
            val file = File(cacheDir, "upi_qr_${reference}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Settlement QR Code").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Intent share fallback: plain text share
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(textIntent, "Share Settlement Request"))
        }
    }
}

