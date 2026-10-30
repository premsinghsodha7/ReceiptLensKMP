package com.premsingh.receiptlens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** A local, high-resolution sample so the OCR flow works without camera or gallery permissions. */
fun createSampleReceipt(): Bitmap {
    val bitmap = Bitmap.createBitmap(1000, 1320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(245, 242, 235))
    val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawRoundRect(RectF(70f, 55f, 930f, 1265f), 28f, 28f, paper)

    fun text(value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        canvas.drawText(value, x, y, paint)
    }

    fun rule(y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(225, 228, 230)
            strokeWidth = 2f
        }
        canvas.drawLine(125f, y, 875f, y, paint)
    }

    val ink = android.graphics.Color.rgb(31, 47, 58)
    val muted = android.graphics.Color.rgb(104, 119, 124)
    text("NORTH STAR MARKET", 125f, 155f, 45f, ink, bold = true)
    text("DUBAI · UNITED ARAB EMIRATES", 125f, 205f, 25f, muted)
    rule(245f)
    text("INVOICE ID: NS-2048", 125f, 300f, 29f, ink)
    text("DATE: 30/10/2026", 125f, 350f, 29f, ink)
    rule(395f)
    text("ITEM", 125f, 455f, 23f, muted, bold = true)
    text("QTY", 690f, 455f, 23f, muted, bold = true)
    text("AMOUNT", 770f, 455f, 23f, muted, bold = true)
    text("Coffee beans", 125f, 520f, 29f, ink)
    text("1", 710f, 520f, 29f, ink)
    text("AED 68.00", 770f, 520f, 26f, ink)
    text("Oat milk", 125f, 580f, 29f, ink)
    text("2", 710f, 580f, 29f, ink)
    text("AED 32.00", 770f, 580f, 26f, ink)
    text("Reusable cup", 125f, 640f, 29f, ink)
    text("1", 710f, 640f, 29f, ink)
    text("AED 24.00", 770f, 640f, 26f, ink)
    rule(700f)
    text("Subtotal", 590f, 770f, 27f, muted)
    text("AED 124.00", 770f, 770f, 27f, ink)
    text("Tax", 590f, 825f, 27f, muted)
    text("AED 11.40", 770f, 825f, 27f, ink)
    rule(860f)
    text("TOTAL", 590f, 930f, 34f, ink, bold = true)
    text("AED 135.40", 770f, 930f, 30f, ink, bold = true)
    text("THANK YOU FOR SHOPPING LOCAL", 125f, 1090f, 24f, muted, bold = true)
    text("A sample receipt · Not a real transaction", 125f, 1140f, 22f, muted)
    return bitmap
}
