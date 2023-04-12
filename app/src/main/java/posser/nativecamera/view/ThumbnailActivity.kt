package posser.nativecamera.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import posser.nativecamera.R

// 缩略图activity
class ThumbnailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thumbnail)
    }
}