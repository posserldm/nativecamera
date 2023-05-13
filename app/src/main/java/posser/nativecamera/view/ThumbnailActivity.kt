package posser.nativecamera.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.GridLayout
import androidx.recyclerview.widget.GridLayoutManager
import posser.nativecamera.R
import posser.nativecamera.adapter.PhotoThumbnailAdapter
import posser.nativecamera.adapter.ThumbnailAdapter
import posser.nativecamera.databinding.ActivityThumbnailBinding
import posser.nativecamera.util.MediaInfo

// 缩略图activity
class ThumbnailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThumbnailBinding
    private var mediaInfoList: List<MediaInfo>? = null
    private val showList = mutableListOf<MediaInfo>()
    private var recAdapter: ThumbnailAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThumbnailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initRecycleView()
    }

    private fun initRecycleView() {
        mediaInfoList = intent.getParcelableArrayListExtra("mediaInfoList")
        Log.i("posserTest", "ThumbnailActivity mediaList = $mediaInfoList")
        mediaInfoList?.let {
            if (it.size >= 50) {
                showList.addAll(it.subList(0, 50))
            } else {
                showList.addAll(it)
            }
            recAdapter = ThumbnailAdapter(showList, this)
            binding.tnPhotoRec.let { rec ->
                rec.adapter = recAdapter
                rec.layoutManager = GridLayoutManager(this@ThumbnailActivity, 4)
            }
        }
    }
}