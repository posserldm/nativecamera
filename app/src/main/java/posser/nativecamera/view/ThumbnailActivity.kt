package posser.nativecamera.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
    private val layoutManager: GridLayoutManager by lazy { GridLayoutManager(this@ThumbnailActivity, 4) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThumbnailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initRecycleView()
        binding.tnPhotoRec.setOnTouchListener { _, event ->
            onTouchEvent(event)
            false
        }
    }

    private fun initRecycleView() {
        mediaInfoList = intent.getParcelableArrayListExtra("mediaInfoList")
        Log.i("posserTest", "ThumbnailActivity mediaList = $mediaInfoList")
        mediaInfoList?.let {
            if (it.size >= 30) {
                showList.addAll(it.subList(0, 30))
            } else {
                showList.addAll(it)
            }
            recAdapter = ThumbnailAdapter(showList, this)
            binding.tnPhotoRec.let { rec ->
                rec.adapter = recAdapter
                rec.layoutManager = layoutManager
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.i("posserTest", "onTouchEvent on called!")
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {}
            MotionEvent.ACTION_MOVE -> {
                Log.i("posserTest", "相册缩略图在滑动")
                val vItem = layoutManager.findLastVisibleItemPosition()
                if (vItem >= (recAdapter!!.itemCount - 15)) {
                    mediaInfoList?.let {
                        val l = if (recAdapter!!.itemCount + 15 >= it.size) {
                            it.subList(recAdapter!!.itemCount, it.size)
                        } else {
                            it.subList(recAdapter!!.itemCount, recAdapter!!.itemCount + 15)
                        }
                        recAdapter?.appendMediaList(l)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {}
        }
        return super.onTouchEvent(event)
    }
}