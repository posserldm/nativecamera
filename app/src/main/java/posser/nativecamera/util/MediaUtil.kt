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
import android.widget.Toast
import java.io.File
import java.io.IOException

data class MediaInfo(
    val uri: Uri,
    val type: Int,
    val addTime: Long
) {
    companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }
}

fun crateMediaMp4File(context: Context): String {
    val filename = "v_${System.currentTimeMillis()}.mp4"
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    val filePath = if (dir == null) {
        filename
    } else {
        "${dir.absolutePath}/$filename"
    }
    Log.i("posserTest", filePath)
    return filePath
}

fun saveVideoToPublicDir(context: Context, filePath: String, saveResultCallback: (videoUri: Uri?, result: Boolean) -> Unit) {
    val filename = filePath.split("/").last()
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/custom/camera")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        try {
            val fileSrc = File(filePath)
            resolver.openOutputStream(it).use { out ->
                out?.write(fileSrc.readBytes())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            fileSrc.delete()
            saveResultCallback(uri, true)
        } catch (e: IOException) {
            saveResultCallback(null, false)
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
        }
    }
}

// 没有适配别的手机，现在中适配了我自己的
object ImageSize {
    val FRONT_CAMERA_IMAGE_SIZE = Size(4608, 3456)
    val BACK_CAMERA_IMAGE_SIZE = Size(2736, 3648)
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

fun getLastMediaUri(context: Context): Uri? {
    val videoList = getVideoList(context)
    val imageList = getImageList(context)
    if (videoList.isNotEmpty() && imageList.isNotEmpty()) {
        return if (videoList.first().addTime > imageList.first().addTime) videoList.first().uri else imageList.first().uri
    }
    if (videoList.isNotEmpty()) {
        return videoList.first().uri
    }
    if (imageList.isNotEmpty()) {
        return imageList.first().uri
    }
    Log.i("posserTest", "您还没有拍照或拍视频哦")
    return null
}

fun getMediaInfoList(context: Context): List<MediaInfo> {
    val videoList = getVideoList(context)
    val imageList = getImageList(context)
    val mediaList = mutableListOf<MediaInfo>()
    if (videoList.isNotEmpty() && imageList.isNotEmpty()) {
        var i = 0
        var j = 0
        while (i < videoList.size && j < imageList.size) {
            if (videoList[i].addTime > imageList[j].addTime) {
                mediaList.add(videoList[i])
                i++
            } else {
                mediaList.add(imageList[j])
                j++
            }
        }
        if (i < videoList.size) {
            mediaList.addAll(videoList.subList(i, videoList.size))
        }
        if (j < imageList.size) {
            mediaList.addAll(imageList.subList(j, imageList.size))
        }
    }
    Log.i("posserTest", "一个查询到了 ${mediaList.size} 个媒体文件")
    return mediaList
}


private fun getImageList(context: Context): List<MediaInfo> {
    val resolver = context.contentResolver
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED)
    val selection = "${MediaStore.Images.Media.DATA} like ?"
    val selectionArgs = arrayOf("%/custom/camera/%")
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    val cursor =
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

    val list = mutableListOf<MediaInfo>()
    cursor?.use { cur ->
        while (cur.moveToNext()) {
            val addTimeCol = cur.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
            val addTime = cur.getLong(addTimeCol)
            val idCol = cur.getColumnIndex(MediaStore.Images.Media._ID)
            val id = cur.getLong(idCol)
            list.add(MediaInfo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), MediaInfo.TYPE_IMAGE, addTime))
        }
    }
    Log.i("posserTest", "一共查询到 ${list.size} 张照片")
    return list
}

private fun getVideoList(context: Context): List<MediaInfo> {

    val resolver = context.contentResolver
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED)
    val selection = "${MediaStore.Video.Media.DATA} like ?"
    val selectionArgs = arrayOf("%/custom/camera/%")
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    val cursor =
        resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sortOrder)

    val list = mutableListOf<MediaInfo>()
    cursor?.use { cur ->
        while (cur.moveToNext()) {
            val addTimeCol = cur.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
            val addTime = cur.getLong(addTimeCol)
            val idCol = cur.getColumnIndex(MediaStore.Video.Media._ID)
            val id = cur.getLong(idCol)
            list.add(MediaInfo(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id), MediaInfo.TYPE_VIDEO, addTime))
        }
    }
    Log.i("posserTest", "一共查询到 ${list.size} 个视频")
    return list
}