package com.tvelu77.crossfocus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp

object HdrMerger {

    fun fuseToFile(files: List<File>, output: File, maxDim: Int = 2048) {
        val bitmaps = files.map { decodeScaled(it, maxDim) }
        val width = bitmaps.minOf { it.width }
        val height = bitmaps.minOf { it.height }
        val src = bitmaps.map { bitmap ->
            IntArray(width * height).also { arr ->
                bitmap.getPixels(arr, 0, width, 0, 0, width, height)
            }
        }
        val midIndex = bitmaps.size / 2
        val sigma2 = 32.0 * 32.0
        val outPixels = IntArray(width * height)
        for (i in 0 until width * height) {
            var wSum = 0.0
            var lSum = 0.0
            for (b in src.indices) {
                val luma = luma(src[b][i])
                val w = exp(-((luma - 128.0) * (luma - 128.0)) / (2.0 * sigma2))
                wSum += w
                lSum += w * luma
            }
            val fused = if (wSum > 0) lSum / wSum else 128.0
            val mid = src[midIndex][i]
            val midLuma = luma(mid).coerceAtLeast(1.0)
            val scale = fused / midLuma
            val r = ((mid shr 16 and 0xFF) * scale).toInt().coerceIn(0, 255)
            val g = ((mid shr 8 and 0xFF) * scale).toInt().coerceIn(0, 255)
            val b = ((mid and 0xFF) * scale).toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        FileOutputStream(output).use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        bitmaps.forEach { it.recycle() }
        out.recycle()
    }

    private fun luma(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    private fun decodeScaled(file: File, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }
}
