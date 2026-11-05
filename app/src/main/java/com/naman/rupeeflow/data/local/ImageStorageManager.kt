package com.naman.rupeeflow.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageManager {

    private const val DIRECTORY_RECEIPTS = "receipts"
    private const val DIRECTORY_PROFILES = "profiles"
    private const val DIRECTORY_GROUPS = "groups"

    private const val MAX_BILL_DIMENSION = 1920
    private const val MAX_PROFILE_DIMENSION = 512
    private const val JPEG_QUALITY = 80

    suspend fun saveBillImage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        saveImageToDirectory(context, sourceUri, DIRECTORY_RECEIPTS, "bill", MAX_BILL_DIMENSION)
    }

    suspend fun saveBillBitmap(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        saveBitmapToDirectory(context, bitmap, DIRECTORY_RECEIPTS, "bill", MAX_BILL_DIMENSION)
    }

    suspend fun saveProfileImage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        saveImageToDirectory(context, sourceUri, DIRECTORY_PROFILES, "profile", MAX_PROFILE_DIMENSION)
    }

    suspend fun saveProfileBitmap(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        saveBitmapToDirectory(context, bitmap, DIRECTORY_PROFILES, "profile", MAX_PROFILE_DIMENSION)
    }

    suspend fun saveGroupImage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        saveImageToDirectory(context, sourceUri, DIRECTORY_GROUPS, "group", MAX_PROFILE_DIMENSION)
    }

    suspend fun saveGroupBitmap(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        saveBitmapToDirectory(context, bitmap, DIRECTORY_GROUPS, "group", MAX_PROFILE_DIMENSION)
    }

    fun deleteImage(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    fun imageExists(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            File(filePath).exists()
        } catch (_: Exception) {
            false
        }
    }

    private fun saveImageToDirectory(
        context: Context,
        sourceUri: Uri,
        directoryName: String,
        prefix: String,
        maxDimension: Int
    ): String? {
        return try {
            val dir = File(context.filesDir, directoryName).apply { if (!exists()) mkdirs() }
            val fileName = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(dir, fileName)

            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream?.close()

            // Correct orientation if EXIF metadata contains rotation
            val orientedBitmap = fixExifOrientation(context, sourceUri, originalBitmap)

            val scaledBitmap = scaleBitmap(orientedBitmap, maxDimension)

            FileOutputStream(destFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }
            if (scaledBitmap != originalBitmap && scaledBitmap != orientedBitmap) {
                scaledBitmap.recycle()
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmapToDirectory(
        context: Context,
        bitmap: Bitmap,
        directoryName: String,
        prefix: String,
        maxDimension: Int
    ): String? {
        return try {
            val dir = File(context.filesDir, directoryName).apply { if (!exists()) mkdirs() }
            val fileName = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(dir, fileName)

            val scaledBitmap = scaleBitmap(bitmap, maxDimension)
            FileOutputStream(destFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), true)
    }

    private fun fixExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            inputStream.close()

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }
}
