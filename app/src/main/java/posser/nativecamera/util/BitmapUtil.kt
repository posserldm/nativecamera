package posser.nativecamera.util

import android.graphics.Bitmap

/**
 * @param bitmap 源 Bitmap
 * @param width 单位 px
 * @param height 单位 px
 */
fun cutBitmapInCenter(bitmap: Bitmap, width: Int, height: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val startW = if (w <= width) w else (w - width) / 2
    val startH = if (h <= height) h else (h - height) / 2
    return Bitmap.createBitmap(bitmap, startH, startW, width, height)
}