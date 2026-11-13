package com.naman.rupeeflow.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {

    private val pages = mutableListOf<PdfDocument.Page>()
    private var isClosed = false

    @Implementation
    fun __constructor__() {
        // Prevent nativeCreateDocument from failing
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        if (isClosed) {
            throw IllegalStateException("document is closed!")
        }
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val constructor = PdfDocument.Page::class.java.getDeclaredConstructor(
            Canvas::class.java,
            PdfDocument.PageInfo::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(canvas, pageInfo)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        if (isClosed) {
            throw IllegalStateException("document is closed!")
        }
        pages.add(page)
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        if (isClosed) {
            throw IllegalStateException("document is closed!")
        }
        val pageCount = if (pages.isEmpty()) 1 else pages.size
        val mockPdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n" +
                "trailer\n<< /Size 1 >>\nstartxref\n0\n%%EOF\n" +
                "A".repeat(pageCount * 4000)
        out.write(mockPdfContent.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    @Implementation
    fun close() {
        isClosed = true
    }
}

