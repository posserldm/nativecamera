package posser.nativecamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import posser.nativecamera.databinding.ActivityMainBinding
import posser.nativecamera.util.checkSelfPermissionCompat
import posser.nativecamera.util.getLastMediaUri
import posser.nativecamera.util.requestPermissionsCompat
import posser.nativecamera.view.fragment.CameraFragment
import posser.nativecamera.view.fragment.CameraPreviewFragment


private const val REQUEST_PERMISSION_CODE = 101
private const val CAMERA_PHOTO_FUNC = CameraFragment.PHOTO
private const val CAMERA_VIDEO_FUNC = CameraFragment.VIDEO

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentCameraType = CameraFragment.REAR_CAMERA

    private val onTakePhotoListener = object : CameraFragment.OnTakePhotoListener {
        override fun onStart() {
        }

        override fun onCompleted() {
        }

        override fun onSavePhotoCompleted(bitmap: Bitmap) {
            runOnUiThread {
                Glide.with(this@MainActivity)
                    .load(bitmap)
                    .apply(RequestOptions.bitmapTransform(CircleCrop()))
                    .into(binding.mainPhotoAlbum)
            }
        }

    }

    private val mediaRecordingStateListener = object : CameraFragment.MediaRecordingStateListener {
        override fun onStart() {
        }

        override fun onStop() {
        }

        override fun onSaveFileSuccess(uri: Uri) {
            runOnUiThread {
                Glide.with(this@MainActivity)
                    .load(uri)
                    .circleCrop()
                    .into(binding.mainPhotoAlbum)
            }
        }

        override fun onSaveFileFail(msg: String) {
        }

    }

    private var currentCameraFunction = CAMERA_PHOTO_FUNC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestPermissions()
        initEvents()
        initFragments()
        initGalleryPhotoSrc()
        testCode()
    }

    override fun onResume() {
        super.onResume()
        checkCameraPreview(currentCameraType)
    }

    private val frontCameraFragment: CameraFragment by lazy { CameraFragment(CameraFragment.FRONT_CAMERA, CAMERA_PHOTO_FUNC) }
    private val rearCameraFragment: CameraFragment by lazy { CameraFragment(CameraFragment.REAR_CAMERA, CAMERA_PHOTO_FUNC) }

    private fun initFragments() {
        frontCameraFragment.setOnTakePhotoListener(onTakePhotoListener)
        rearCameraFragment.setOnTakePhotoListener(onTakePhotoListener)
        frontCameraFragment.setOnMediaRecordingStateListener(mediaRecordingStateListener)
        rearCameraFragment.setOnMediaRecordingStateListener(mediaRecordingStateListener)
    }

    private fun initGalleryPhotoSrc() {
        val uri = getLastMediaUri(this)
        uri?.let {
            Glide.with(this)
                .load(it)
                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                .into(binding.mainPhotoAlbum)
        }
    }

    private var openedMediaRecord = false
    private fun initEvents() {
        // 前后相机切换
        binding.mainCameraTypeSelector.setOnClickListener {
            currentCameraType = if (currentCameraType == CameraFragment.REAR_CAMERA) {
                CameraFragment.FRONT_CAMERA
            } else {
                CameraFragment.REAR_CAMERA
            }
            checkCameraPreview(currentCameraType)
        }

        // 拍照或开启/关闭录像
        binding.mainCameraSwitch.setOnClickListener {
            if (currentCameraFunction == CAMERA_PHOTO_FUNC) {
                getCurrentCameraFragment().takePhoto()
            } else {
                getCurrentCameraFragment().startOrEndMediaRecord()
                Glide.with(this)
                    .load(if (openedMediaRecord) R.drawable.video_start_record2 else R.drawable.video_stop_record)
                    .apply(RequestOptions.bitmapTransform(CircleCrop()))
                    .into(binding.mainCameraSwitch)
                openedMediaRecord = !openedMediaRecord
            }
        }
        // 拍照功能选择
        binding.mainCameraSelector.setOnClickListener {
            if (currentCameraFunction != CAMERA_PHOTO_FUNC) {
                binding.mainVideoSelector.setBackgroundResource(R.drawable.textview_border)
                binding.mainCameraSelector.setBackgroundResource(R.drawable.textview_selected_border)
                Glide.with(this)
                    .load(R.drawable.camera)
                    .apply(RequestOptions.bitmapTransform(CircleCrop()))
                    .into(binding.mainCameraSwitch)
                currentCameraFunction = CAMERA_PHOTO_FUNC
                getCurrentCameraFragment().apply {
                    cameraMode = currentCameraFunction
                    enterTakePhotoMode(true)
                }
            }
        }
        // 录像功能选择
        binding.mainVideoSelector.setOnClickListener {
            if (currentCameraFunction != CAMERA_VIDEO_FUNC) {
                binding.mainCameraSelector.setBackgroundResource(R.drawable.textview_border)
                binding.mainVideoSelector.setBackgroundResource(R.drawable.textview_selected_border)
                Glide.with(this)
                    .load(R.drawable.video_start_record2)
                    .apply(RequestOptions.bitmapTransform(CircleCrop()))
                    .into(binding.mainCameraSwitch)
                currentCameraFunction = CAMERA_VIDEO_FUNC
                getCurrentCameraFragment().apply {
                    cameraMode = currentCameraFunction
                    enterMediaRecordMode()
                }
            }
        }
    }

    private fun getCurrentCameraFragment(): CameraFragment {
        return if (currentCameraType == CameraFragment.REAR_CAMERA) {
            rearCameraFragment
        } else {
            frontCameraFragment
        }
    }

    private fun checkCameraPreview(cameraType: Int) {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.main_stream_media_fragment,
                if (cameraType == CameraPreviewFragment.FRONT_CAMERA) frontCameraFragment else rearCameraFragment,
                CameraPreviewFragment::class.java.simpleName
            ).commit()
    }

    private fun requestPermissions() {
        val progressions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        for (p in progressions) {
            if (checkSelfPermissionCompat(p) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionsCompat(progressions, REQUEST_PERMISSION_CODE)
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (g in grantResults) {
                if (g != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "请前往设置->应用->权限管理->打开存储权限、读写权限、相机权限、录音权限，否则无法使用相关功能", Toast.LENGTH_LONG).show()
                    break
                }
            }
        }
    }

    private fun testCode() {

    }
}