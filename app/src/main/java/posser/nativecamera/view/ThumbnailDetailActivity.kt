package posser.nativecamera.view

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.bumptech.glide.Glide
import posser.nativecamera.R
import posser.nativecamera.databinding.ActivityThumbnailDetailBinding
import posser.nativecamera.dialog.buildMediaDetail
import posser.nativecamera.util.MediaInfo
import java.text.SimpleDateFormat
import java.util.concurrent.Executors

// 缩略图详情activity
class ThumbnailDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThumbnailDetailBinding
    private val format = SimpleDateFormat("yyyy年MM月dd HH:mm")
    private lateinit var mediaInfo:MediaInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("posserTest", "ThumbnailDetailActivity onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivityThumbnailDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mediaInfo = intent.getParcelableExtra("mediaInfo")!!
        initToolbar()
        initContent()
    }

    private fun initToolbar() {
        // 显示媒体文件详情
        binding.tndDetailBtn.setOnClickListener {
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                val info = mediaInfo
                contentResolver.openInputStream(info.uri).use { input ->
                    var height = 0
                    var width = 0
                    var sizeInMB = 0
                    if (info.type == MediaInfo.TYPE_IMAGE) {
                        val bitmap = BitmapFactory.decodeStream(input)
                        height = bitmap.height
                        width = bitmap.width
                        sizeInMB = bitmap.byteCount / 1024 / 1024
                    } else {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(this, info.uri)
                        width =
                            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                ?: "0").toInt()
                        height =
                            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                ?: "0").toInt()
                        input?.readBytes()?.let { bys -> sizeInMB = bys.size / 1024 / 1024 }
                    }

                    val data = info.data
                    val msg = "文件名：${data.split("/").last()}\n" +
                            "创建时间：${
                                format.format(data.split("/").last()
                                    .split(".")
                                    .first().filter { it.isDigit() }.toLong())
                            }\n" +
                            "尺寸：$width X $height\n" +
                            "文件大小：$sizeInMB MB\n" +
                            "存储位置：$data"
                    Log.i("posserTest", "msg = $msg")

                    runOnUiThread {
                        buildMediaDetail(this@ThumbnailDetailActivity, msg)
                    }
                }
            }
        }
        // 返回
        binding.tndBackBtn.setOnClickListener {
            finish()
        }
    }

    private fun initContent() {
        Glide.with(this)
            .load(mediaInfo.uri)
            .into(binding.tndViewContent)
    }
}