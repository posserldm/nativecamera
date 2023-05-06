package posser.nativecamera.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

data class MediaEntity(
    val uri: Uri,
    val type: String
) {
    companion object {
        const val IMAGE = "image/jpeg"
        const val VIDEO = "video/mp4"
    }
}

interface IMediaHolder {
    fun bind(entity: MediaEntity)
}

abstract class MediaHolder(view: View): IMediaHolder, RecyclerView.ViewHolder(view)

class ImageHolder(view: View) : MediaHolder(view) {
    override fun bind(entity: MediaEntity) {
        TODO("Not yet implemented")
    }

}

class VideoHolder(view: View) : MediaHolder(view) {
    override fun bind(entity: MediaEntity) {
        TODO("Not yet implemented")
    }

}

class MediaAdapter(
    medias: List<MediaEntity>
): RecyclerView.Adapter<MediaHolder>() {

    private val medias = mutableListOf<MediaEntity>()

    init {
        this.medias.addAll(medias)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder {
        TODO()
    }

    override fun onBindViewHolder(holder: MediaHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        return medias.size
    }

}