package posser.nativecamera.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import java.io.File
import java.io.IOException
import kotlin.math.sin

fun crateMediaMp4File(context: Context): String {
    val filename = "${System.currentTimeMillis()}.mp4"
    val dir = context.getExternalFilesDir(null)

    return if (dir == null) {
        filename
    } else {
        "${dir.absolutePath}/$filename"
    }
}

// 没有适配别的手机，现在中适配了我自己的
object ImageSize {
    val FRONT_CAMERA_IMAGE_SIZE = Size(4608, 3456)
    val BACK_CAMERA_IMAGE_SIZE = Size(2736,3648)
}

/**
 * @param saveResultCallback callback param variable result, if result true save success; false: save failed
 */
fun saveImageToGallery(context: Context, bitmap: Bitmap, saveResultCallback: (photoUri: Uri?, result: Boolean) -> Unit) {
    val fileName = "${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        /**
         * 这段代码的作用是为保存的图片指定一个相对路径，
         * 并且设置该图片的IS_PENDING属性为1，表示该图片正在被处理，
         * 尚未准备好。这是在Android Q（API Level 29）及以上版本中使用的新特性，
         * 即Scoped Storage（作用域存储）模式，该模式对应用程序访问存储设备上的文件进行了限制。
         * 在Scoped Storage模式下，应用程序只能在应用程序私有目录和应用程序创建的公共目录中访问文件，
         * 其他目录只能通过系统提供的API进行访问。因此，为了保存图片到公共目录中，需要指定一个相对路径，
         * 然后通过MediaStore API将图片插入到系统的MediaStore数据库中。插入成功后，需要将该图片的
         * IS_PENDING属性设置为0，表示该图片已经准备好，可以被其他应用程序访问。
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}${File.separator}custom${File.separator}camera"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

    }

    val resolver = context.contentResolver
    val uri = resolver?.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        try {
            val stream = resolver.openOutputStream(it)
            if (stream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                stream.close()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                saveResultCallback(uri, true)
            }
        } catch (e: IOException) {
            saveResultCallback(null, false)
        }
    }
}

fun getLastMediaUri(context: Context):Uri? {
    val contentResolver = context.contentResolver
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED)
    val selection = "${MediaStore.Files.FileColumns.DATA} like ?"
    val selectionArgs = arrayOf("%/custom/camera/%")
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    val cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)
    cursor?.use {
        if (it.moveToFirst()) {
            val columnIndex = it.getColumnIndex(MediaStore.Images.Media._ID)
            val i = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val id = it.getLong(columnIndex)
            return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }

    }
    return null

}
