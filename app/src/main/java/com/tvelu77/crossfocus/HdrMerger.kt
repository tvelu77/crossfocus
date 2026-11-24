package com.tvelu77.crossfocus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.pow

object HdrMerger {

    fun fuseToFile(frames: List<Pair<File, Int>>, output: File, maxDim: Int = 2048) {
        val bitmaps = frames.map { decodeScaled(it.first, maxDim) }
        val width = bitmaps.minOf { it.width }
        val height = bitmaps.minOf { it.height }
        val src = bitmaps.map { bitmap ->
            IntArray(width * height).also { arr ->
                bitmap.getPixels(arr, 0, width, 0, 0, width, height)
            }
        }
        val gains = frames.map { (_, evIndex) -> 2.0.pow(evIndex / 6.0) }
        val sigma2 = 32.0 * 32.0
        val outPixels = IntArray(width * height)
        for (i in 0 until width * height) {
            var wSum = 0.0
            var rSum = 0.0
            var gSum = 0.0
            var bSum = 0.0
            for (k in src.indices) {
                val p = src[k][i]
                val r = ((p shr 16) and 0xFF) / gains[k]
                val g = ((p shr 8) and 0xFF) / gains[k]
                val b = (p and 0xFF) / gains[k]
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                val w = exp(-((luma - 128.0) * (luma - 128.0)) / (2.0 * sigma2))
                wSum += w
                rSum += w * r
                gSum += w * g
                bSum += w * b
            }
            val inv = if (wSum > 0) 1.0 / wSum else 0.0
            val r = (rSum * inv).toInt().coerceIn(0, 255)
            val g = (gSum * inv).toInt().coerceIn(0, 255)
            val b = (bSum * inv).toInt().coerceIn(0, 255)
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
