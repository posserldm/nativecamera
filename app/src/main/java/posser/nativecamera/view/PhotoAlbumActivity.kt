package posser.nativecamera.view

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.drew.imaging.ImageMetadataReader
import posser.nativecamera.adapter.PhotoThumbnailAdapter
import posser.nativecamera.databinding.ActivityPhotoAblumBinding
import posser.nativecamera.dialog.buildMediaDetail
import posser.nativecamera.util.MediaInfo
import posser.nativecamera.util.getMediaInfoList
import java.text.SimpleDateFormat
import java.util.concurrent.Executors

// 相册activity
class PhotoAlbumActivity : AppCompatActivity() {

    val format = SimpleDateFormat("yyyy年MM月dd HH:mm")

    private lateinit var binding: ActivityPhotoAblumBinding
    private lateinit var mediaInfoList: List<MediaInfo>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoAblumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mediaInfoList = getMediaInfoList(this)
        initRecycleView()
        initEvent()
    }

    private var currentPageIndex = 0
    private fun initRecycleView() {
        val list = if (mediaInfoList.size > 15) {
            mediaInfoList.subList(0, 15)
        } else {
            mediaInfoList
        }
        val pAdapter = PhotoThumbnailAdapter(list, this)
        binding.paViewPager2.apply {
            adapter = pAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        binding.paViewPager2.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (pAdapter.itemCount - position < 5 && pAdapter.itemCount < mediaInfoList.size) {
                        val l = if (mediaInfoList.size - pAdapter.itemCount >= 10) {
                            mediaInfoList.subList(0, pAdapter.itemCount + 10)
                        } else {
                            mediaInfoList
                        }
                        pAdapter.appendMediaList(l)
                    }

                    currentPageIndex = position
                    Log.i("posserTest", "data = ${mediaInfoList[position].data}")
                    val time = mediaInfoList[position].data
                        .split("/")
                        .last()
                        .split(".")
                        .first()
                        .filter { it.isDigit() }
                    Log.i("posserTest", "time = $time")
                    binding.paTitle.text = format.format(time.toLong())
                }
            }
        )

    }

    private fun initEvent() {
        // 启动相册缩略图
        binding.paPhotoList.setOnClickListener {
            val i = Intent(this, ThumbnailActivity::class.java)
            i.putExtra("mediaInfoList", mediaInfoList.toTypedArray())
            startActivity(i)
        }
        // 显示媒体文件详情
        binding.paPhotoInfo.setOnClickListener {
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                val info = mediaInfoList[currentPageIndex]
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
                        buildMediaDetail(this@PhotoAlbumActivity, msg)
                    }
                }
            }
        }
    }

}
