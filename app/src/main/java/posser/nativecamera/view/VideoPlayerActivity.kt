package posser.nativecamera.view

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import posser.nativecamera.databinding.ActivityVideoPlayerBinding
import java.util.concurrent.atomic.AtomicBoolean

class VideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var binding: ActivityVideoPlayerBinding
    private val playerIdle = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("posserTest", "VideoPlayerActivity onCreate")
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initPlayer()
        initTextureViewEvent()
        initPlayControllerViewEvent()
    }

    private fun initPlayer() {
        val uri: Uri? = intent.getParcelableExtra("mediaInfo")
        uri?.let {
            player = ExoPlayer.Builder(this).build()
            player?.let { p ->
                val mediaItem = MediaItem.fromUri(it)
                p.setMediaItem(mediaItem)
                p.setVideoTextureView(binding.videoTextureView)
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        super.onPlaybackStateChanged(state)
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                // 播放器正在缓冲
                            }
                            Player.STATE_READY -> {
                                Log.i("posserTest", "video read to play!")
                                binding.exoPlayerControl.showTimeoutMs = (p.duration + 1000).toInt()
                                // 播放器准备好了
                                p.play()
                            }
                            Player.STATE_ENDED -> {
                                // 播放器播放结束
                                playerIdle.set(true)
                            }
                            Player.STATE_IDLE -> {
                                // 播放器处于空闲状态
                                playerIdle.set(true)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        showToast("播放出错，错误信息：${error.message}")
                    }
                })
                p.prepare()
                binding.exoPlayerControl.player = p
            }
        }
    }

    private fun initTextureViewEvent() {
        binding.videoTextureView.setOnClickListener {
            if (playerIdle.compareAndSet(true, false)) {
                Log.i("posserTest", "video texture view clicked!")
                player?.let {
                    binding.videoTexturePlayBtn.visibility = View.GONE
                    binding.exoPlayerControl.show()
                    it.seekTo(0)
                }
            }
        }
    }

    private fun initPlayControllerViewEvent() {
        binding.exoPlayerControl.addVisibilityListener {
            when (it) {
                View.GONE -> {
                    Log.i("posserTest", "exoPlayerControl gone")
                    binding.videoTexturePlayBtn.visibility = View.VISIBLE
                }
                View.VISIBLE -> {
                    Log.i("posserTest", "exoPlayerControl visible")
                    binding.videoTexturePlayBtn.visibility = View.GONE
                }
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}