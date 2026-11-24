package com.tvelu77.crossfocus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.pow

object HdrMerger {

    fun fuseToFile(frames: List<Pair<File, Int>>, output: File, maxDim: Int = 2048) {
        val sorted = frames.sortedBy { it.second }
        val baseIdx = sorted.indices.minByOrNull { abs(sorted[it].second) } ?: 0
        val darkIdx = 0
        val brightIdx = sorted.size - 1
        val bitmaps = sorted.map { decodeScaled(it.first, maxDim) }
        val width = bitmaps.minOf { it.width }
        val height = bitmaps.minOf { it.height }
        val src = bitmaps.map { bitmap ->
            IntArray(width * height).also { arr ->
                bitmap.getPixels(arr, 0, width, 0, 0, width, height)
            }
        }
        val baseEv = sorted[baseIdx].second
        val gains = sorted.map { (_, ev) -> 2.0.pow((ev - baseEv) / 6.0) }
        val outPixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val bp = src[baseIdx][i]
            var r = ((bp shr 16) and 0xFF).toDouble()
            var g = ((bp shr 8) and 0xFF).toDouble()
            var b = (bp and 0xFF).toDouble()
            val lumaBase = 0.299 * r + 0.587 * g + 0.114 * b
            if (brightIdx != baseIdx) {
                val w = smoothstep(230.0, 250.0, lumaBase)
                if (w > 0.0) {
                    val p = src[brightIdx][i]
                    r = r * (1.0 - w) + (((p shr 16) and 0xFF) / gains[brightIdx]) * w
                    g = g * (1.0 - w) + (((p shr 8) and 0xFF) / gains[brightIdx]) * w
                    b = b * (1.0 - w) + ((p and 0xFF) / gains[brightIdx]) * w
                }
            }
            if (darkIdx != baseIdx && darkIdx != brightIdx) {
                val w = 1.0 - smoothstep(5.0, 25.0, lumaBase)
                if (w > 0.0) {
                    val p = src[darkIdx][i]
                    r = r * (1.0 - w) + (((p shr 16) and 0xFF) / gains[darkIdx]) * w
                    g = g * (1.0 - w) + (((p shr 8) and 0xFF) / gains[darkIdx]) * w
                    b = b * (1.0 - w) + ((p and 0xFF) / gains[darkIdx]) * w
                }
            }
            outPixels[i] = (0xFF shl 24) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, width, 0, 0, width, height)
        FileOutputStream(output).use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        bitmaps.forEach { it.recycle() }
        out.recycle()
    }

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
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
