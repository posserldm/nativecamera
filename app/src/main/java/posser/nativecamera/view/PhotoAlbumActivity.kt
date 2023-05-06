package posser.nativecamera.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import posser.nativecamera.adapter.PhotoThumbnailAdapter
import posser.nativecamera.databinding.ActivityPhotoAblumBinding
import posser.nativecamera.util.MediaInfo
import posser.nativecamera.util.getMediaInfoList

// 相册activity
class PhotoAlbumActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoAblumBinding
    private lateinit var mediaInfoList: List<MediaInfo>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoAblumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mediaInfoList = getMediaInfoList(this)
        initRecycleView()
    }

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
                }
            }
        )

    }
}
