package posser.nativecamera.view.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import posser.nativecamera.databinding.FragmentCameraBinding
import posser.nativecamera.util.ImageSize
import posser.nativecamera.util.crateMediaMp4File
import posser.nativecamera.util.saveImageToGallery
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val FRONT_CAMERA_ROTATION = 270
private const val BACK_CAMERA_ROTATION = 90

class CameraFragment(
    private val cameraType: Int,
    var cameraMode: Int
) : Fragment() {

    companion object {
        // 我的手机后面有几个摄像头，如果直接获取面向的方法可能会得到广角的摄像头，我这里默认0为后置，1为前置
        const val REAR_CAMERA = 0
        const val FRONT_CAMERA = 1
        const val VIDEO = 2
        const val PHOTO = 3
    }

    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThread${System.currentTimeMillis()}")
        backgroundHandlerThread.start()
        backgroundHandler = Handler(backgroundHandlerThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread.quitSafely()
        backgroundHandlerThread.join()
    }

    private var binding: FragmentCameraBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        startBackgroundThread()
        return binding?.root
    }


    override fun onResume() {
        super.onResume()
        initImageReader()
        initCameraPreviewTexture()
        if (cameraMode == PHOTO) {
            enterTakePhotoMode()
        } else {
            enterMediaRecordMode()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i("posserTest", "onPause")
        releaseCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundThread()
        binding = null
    }

    private lateinit var cameraManager: CameraManager
    @Volatile
    private lateinit var cameraDevice: CameraDevice
    private var cameraId: String = ""

    private fun initCamera() {
        try {
            cameraId = cameraManager.cameraIdList[cameraType]
            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    this@CameraFragment.cameraDevice = camera
                    startTakePhotoPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    releaseCamera()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    releaseCamera()
                }
            }

            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun releaseCamera() {
        Log.i("posserTest", "releaseCamera")
        cameraDevice.close()
    }

    private fun cameraReady(): Boolean {
        return this::cameraDevice.isInitialized
    }

    private fun initCameraPreviewTexture() {
        if (binding?.cameraPreview?.surfaceTextureListener != null) {
            Log.i("posserTest", "resume恢复过来")
            initCamera()
        }

        binding?.cameraPreview?.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    initCamera()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    releaseCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        cameraManager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun enterTakePhotoMode(isSwitchMode: Boolean = false) {
        if (cameraMode == PHOTO && isSwitchMode) return
        if (recording) {
            endMediaRecord()
        }
        cameraMode = PHOTO
    }

    private lateinit var takePhotoRequestBuilder: CaptureRequest.Builder
    fun takePhoto() {
        try {
            // initImageReader()
            prepareTakePhotoRequestBuilder()
            stopTakePhotoPreview()
            onTakePhotoListener?.onStart()
            takePhotoCameraCaptureSession.capture(takePhotoRequestBuilder.build(), object :
                CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.i("posserTest", "take photo mode onCaptureCompleted")
                    restartTakePhotoPreview()
                    onTakePhotoListener?.onCompleted()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            showToast(e.message)
        }
    }

    private fun prepareTakePhotoRequestBuilder() {
        if (!this::takePhotoRequestBuilder.isInitialized) {
            takePhotoRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        }
        /*
         * CAPTURE_INTENT：这是一个整数值，指定了拍照的意图，如拍摄照片或录制视频。
         * CONTROL_MODE：这是一个整数值，指定了相机设备如何控制图像捕获的过程，例如自动控制或手动控制。
         * FLASH_MODE：这是一个整数值，指定了闪光灯的模式，如自动、关闭或打开。
         * SENSOR_SENSITIVITY：这是一个整数值，指定了感光度，通常称为ISO值。
         * SENSOR_EXPOSURE_TIME：这是一个长整数值，指定了曝光时间，以纳秒为单位。
         * JPEG_ORIENTATION：这是一个整数值，指定了JPEG图像的方向。
         * JPEG_QUALITY：这是一个整数值，指定了JPEG图像的压缩质量。
         * CONTROL_AF_MODE：这是一个整数值，指定了自动对焦的模式，如自动对焦、连续自动对焦或手动对焦。
         */
        takePhotoRequestBuilder.let {
            it.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)  //自动对焦
            it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) //自动爆光
            // 这里不做多机适配，只适配了我自己的手机
            val rotation =
                if (cameraType == FRONT_CAMERA) FRONT_CAMERA_ROTATION else BACK_CAMERA_ROTATION
            it.set(CaptureRequest.JPEG_ORIENTATION, rotation)
            val surface = imageReader.surface
            it.addTarget(surface)
        }
    }

    private lateinit var takePhotoPreviewRequestBuilder: CaptureRequest.Builder
    private lateinit var takePhotoCameraCaptureSession: CameraCaptureSession
    private lateinit var takePhotoPreviewSurface: Surface
    private lateinit var takePhotoConfigurationList: List<OutputConfiguration>
    private lateinit var threadPool: ExecutorService
    private fun startTakePhotoPreview() {
        if (!cameraReady()) {
            Log.i("posserTest", "相机未准备好!")
            showToast("相机未准备好!")
            return
        }
        Log.i("posserTest", "相机准备完毕!")
        takePhotoPreviewSurface = Surface(binding?.cameraPreview?.surfaceTexture)
        takePhotoPreviewRequestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(takePhotoPreviewSurface)
            }
        val takePhotoCaptureCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                takePhotoCameraCaptureSession = session
                takePhotoCameraCaptureSession.setRepeatingRequest(takePhotoPreviewRequestBuilder.build(), null, backgroundHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                releaseCamera()
            }
        }
        takePhotoConfigurationList = listOf(
            OutputConfiguration(takePhotoPreviewSurface),
            OutputConfiguration(imageReader.surface)
        )
        if (!this::threadPool.isInitialized) {
            threadPool = Executors.newFixedThreadPool(5)
        }
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, takePhotoConfigurationList, threadPool, takePhotoCaptureCallback)

        cameraDevice.createCaptureSession(sessionConfiguration)
    }

    private fun stopTakePhotoPreview() {
        takePhotoCameraCaptureSession.stopRepeating()
    }

    private fun restartTakePhotoPreview() {
        takePhotoCameraCaptureSession.apply {
            stopRepeating()
            setRepeatingRequest(takePhotoPreviewRequestBuilder.build(), null, backgroundHandler)
        }
    }

    interface OnTakePhotoListener {
        fun onStart()
        fun onCompleted()
        fun onSavePhotoCompleted(bitmap: Bitmap)
    }

    private var onTakePhotoListener: OnTakePhotoListener? = null
    fun setOnTakePhotoListener(listener: OnTakePhotoListener) {
        onTakePhotoListener = listener
    }

    private lateinit var imageReader: ImageReader
    private fun initImageReader() {
        Log.i("posserTest", "initImageReader")
        imageReader = if (cameraType == FRONT_CAMERA) {
            ImageReader.newInstance(ImageSize.FRONT_CAMERA_IMAGE_SIZE.width, ImageSize.FRONT_CAMERA_IMAGE_SIZE.height, ImageFormat.JPEG, 2)
        } else {
            ImageReader.newInstance(ImageSize.BACK_CAMERA_IMAGE_SIZE.width, ImageSize.BACK_CAMERA_IMAGE_SIZE.height, ImageFormat.JPEG, 2)
        }
        imageReader.setOnImageAvailableListener({ reader ->
            Log.i("posserTest", "OnImageAvailableListener")
            // image.acquireLatestImage();//从ImageReader的队列中获取最新的image,删除旧的
            // image.acquireNextImage();//从ImageReader的队列中获取下一个图像,如果返回null没有新图像可用
            val image: Image = reader.acquireNextImage()
            try {
                //  这里的image.getPlanes()[0]其实是图层的意思,因为我的图片格式是JPEG只有一层所以是geiPlanes()[0],
                //  如果你是其他格式(例如png)的图片会有多个图层,就可以获取指定图层的图像数据　　　　　　　
                val byteBuffer: ByteBuffer = image.planes[0].buffer
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)
                // 存到相册当中去
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                saveImageToGallery(requireActivity(), bitmap) { photoUri, result ->
                    if (result && photoUri != null) {
                        onTakePhotoListener?.onSavePhotoCompleted(bitmap)
                    }
                }
                image.close()
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }, backgroundHandler)
    }

    private var recording = false
    fun enterMediaRecordMode() {
        if (cameraMode == VIDEO) return
        cameraMode = VIDEO
    }

    fun startOrEndMediaRecord() {
        if (recording) {
            endMediaRecord()
        } else {
            startMediaRecord()
        }
    }

    private lateinit var mediaRecorder: MediaRecorder
    private fun initMediaRecorder() {
        if (!this::mediaRecorder.isInitialized) {
            // 创建一个MediaRecorder对象
            mediaRecorder = MediaRecorder()
        }
        mediaRecorder.apply {
            reset()
            // 设置视频源为Surface
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            // 设置音频源为麦克风
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // 设置输出格式为MPEG_4
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            // 设置输出文件
            setOutputFile(crateMediaMp4File(this@CameraFragment.requireContext()))
            // 设置视频编码的比特率
            setVideoEncodingBitRate(10000000)
            // 设置视频帧率
            setVideoFrameRate(30)
            // 设置视频大小
            setVideoSize(1920, 1080)
            // 设置视频编码器为H.264
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            // 设置音频编码器为AAC
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
    }

    private lateinit var mediaRecordRequestBuilder: CaptureRequest.Builder
    private lateinit var mediaRecorderCaptureSession: CameraCaptureSession
    private lateinit var mediaRecorderCaptureCallback: CameraCaptureSession.StateCallback
    private lateinit var mediaRecorderPreviewSurface: Surface
    private lateinit var mediaRecorderConfigurationList: List<OutputConfiguration>
    private lateinit var mediaRecorderThreadPool: ExecutorService
    private fun startMediaRecordPreview() {
        mediaRecordRequestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(takePhotoPreviewSurface)
            }
        mediaRecorderPreviewSurface = Surface(binding?.cameraPreview?.surfaceTexture)
        mediaRecorderCaptureCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                mediaRecorderCaptureSession = session
                mediaRecorderCaptureSession.setRepeatingRequest(mediaRecordRequestBuilder.build(), null, backgroundHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                releaseCamera()
            }
        }
        mediaRecorderConfigurationList = listOf(
            OutputConfiguration(mediaRecorderPreviewSurface)
        )
        if (!this::mediaRecorderThreadPool.isInitialized) {
            mediaRecorderThreadPool = Executors.newFixedThreadPool(5)
        }
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, mediaRecorderConfigurationList, mediaRecorderThreadPool, mediaRecorderCaptureCallback)

        cameraDevice.createCaptureSession(sessionConfiguration)
    }

    private fun startMediaRecord() {
        initMediaRecorder()
        mediaRecorder.prepare()
        mediaRecordRequestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(takePhotoPreviewSurface)
                addTarget(mediaRecorder.surface)
            }
        mediaRecorderPreviewSurface = Surface(binding?.cameraPreview?.surfaceTexture)
        mediaRecorderCaptureCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                mediaRecorderCaptureSession = session
                mediaRecorderCaptureSession.setRepeatingRequest(mediaRecordRequestBuilder.build(), null, backgroundHandler)
                mediaRecorder.start()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                releaseCamera()
            }
        }
        mediaRecorderConfigurationList = listOf(
            OutputConfiguration(mediaRecorderPreviewSurface),
            OutputConfiguration(mediaRecorder.surface)
        )
        if (!this::mediaRecorderThreadPool.isInitialized) {
            mediaRecorderThreadPool = Executors.newFixedThreadPool(5)
        }
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, mediaRecorderConfigurationList, mediaRecorderThreadPool, mediaRecorderCaptureCallback)

        cameraDevice.createCaptureSession(sessionConfiguration)
    }

    private fun endMediaRecord() {
        mediaRecorder.stop()
        startMediaRecordPreview()
        recording = false
    }

    private fun showToast(msg: String?) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

}