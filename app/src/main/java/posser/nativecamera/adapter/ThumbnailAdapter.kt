package posser.nativecamera.adapter

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import posser.nativecamera.R
import posser.nativecamera.util.MediaInfo
import posser.nativecamera.util.dp2px
import posser.nativecamera.view.ThumbnailDetailActivity
import posser.nativecamera.view.VideoPlayerActivity

// 相册缩略图列表
class ThumbnailHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val mediaItem: ImageView = view.findViewById(R.id.thumbnail_item)
    private val vpView: ImageView = view.findViewById(R.id.thumbnail_video_player)
    fun bind(mediaInfo: MediaInfo, showPlayerViewCallback: (isPhoto: Boolean) -> Unit) {
        Glide.with(mediaItem.context)
            .load(mediaInfo.uri)
            .override(dp2px(vpView.context, 100f).toInt(), dp2px(vpView.context, 100f).toInt())
            .fitCenter()
            .into(mediaItem)
        if (mediaInfo.type == MediaInfo.TYPE_VIDEO) {
            vpView.visibility = View.VISIBLE
            vpView.setOnClickListener {
                showPlayerViewCallback(false)
            }
        } else {
            vpView.visibility = View.GONE
            mediaItem.setOnClickListener {
                showPlayerViewCallback(true)
            }
        }
    }
}

class ThumbnailAdapter(
    list: List<MediaInfo>,
    context: Context
) : RecyclerView.Adapter<ThumbnailHolder>() {

    private val mList = mutableListOf<MediaInfo>().apply {
        addAll(list)
    }

    private val mContext = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.thumbnail_item, parent, false)
        return ThumbnailHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbnailHolder, position: Int) {
        holder.bind(mList[position]) {
            val intent = if (it) {
                Log.i("posserTest", "ThumbnailDetailActivity be called!")
                Intent(mContext, ThumbnailDetailActivity::class.java).apply {
                    putExtra("mediaInfo", mList[position])
                }
            } else {
                Intent(mContext, VideoPlayerActivity::class.java).apply {
                    putExtra("mediaInfo", mList[position].uri)
                }
            }

            mContext.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = mList.size

    fun appendMediaList(list: List<MediaInfo>) {
        val start = mList.size
        mList.addAll(list)
        notifyItemRangeInserted(start, mList.size)
    }
}