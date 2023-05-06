package posser.nativecamera.adapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.contentValuesOf
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.audio.AudioCapabilitiesReceiver.Listener
import posser.nativecamera.R
import posser.nativecamera.util.MediaInfo
import posser.nativecamera.view.VideoPlayerActivity

// 相册缩略图展示
class PhotoThumbnailHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val mediaItem:ImageView = view.findViewById(R.id.media_item)
    private val vpView:ImageView = view.findViewById(R.id.video_player)
    fun bind(mediaInfo: MediaInfo, showPlayerViewCallback: () -> Unit) {
        Glide.with(mediaItem.context)
            .load(mediaInfo.uri)
            .into(mediaItem)
        if (mediaInfo.type == MediaInfo.TYPE_VIDEO) {
            vpView.visibility = View.VISIBLE
            vpView.setOnClickListener {
                if (vpView.visibility == View.VISIBLE) {
                    showPlayerViewCallback()
                }
            }
        } else {
            vpView.visibility = View.GONE
        }
    }
}

class PhotoThumbnailAdapter(
    list: List<MediaInfo>,
    context: AppCompatActivity
) : RecyclerView.Adapter<PhotoThumbnailHolder>() {

    private val mList = mutableListOf<MediaInfo>().apply {
        addAll(list)
    }

    private val mContext = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoThumbnailHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.media_info_item, parent, false)
        return PhotoThumbnailHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoThumbnailHolder, position: Int) {
        holder.bind(mList[position]) {
            val intent = Intent(mContext, VideoPlayerActivity::class.java)
            intent.putExtra("mediaInfo", mList[position].uri)
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
