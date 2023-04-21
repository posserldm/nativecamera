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
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import posser.nativecamera.databinding.FragmentCameraPreviewBinding
import posser.nativecamera.util.ImageSize
import posser.nativecamera.util.crateMediaMp4File
import posser.nativecamera.util.saveImageToGallery
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors


// 前置相机实现视频画面
/*
 *  1. 检查相机权限和相机是否可用
 *    在使用相机前，需要检查应用程序是否已被授予相机权限以及相机是否可用。
 * 2. 创建一个CameraManager对象
 *    通过调用Context.getSystemService(Context.CAMERA_SERVICE)，可以获取一个CameraManager对象，该对象允许您列出和选择可用的相机。
 * 3. 选择一个相机
 *    通过调用CameraManager.getCameraIdList()，可以获取所有可用相机的ID列表。从中选择一个要使用的相机，并调用CameraManager.openCamera()打开它。
 * 4. 创建一个相机预览会话
 *    使用相机预览的最常见方法是创建一个相机预览会话。在这里，您需要为预览创建一个Surface，然后将其添加到预览输出中。
 * 5. 开始预览
 *    在创建好预览会话后，调用CameraCaptureSession.setRepeatingRequest()开始预览。 建议使用 textTrueView控件
 * 6. 拍照
 *    在拍照之前，您需要创建一个CaptureRequest对象，然后使用CameraCaptureSession.capture()方法捕获图像。
 * 7. 释放相机资源
 * 在完成相机操作后，您需要释放相机资源，以确保其他应用程序可以使用相机。您可以通过调用CameraDevice.close()来实现这一点
 */

private const val FRONT_CAMERA_ROTATION = 270
private const val BACK_CAMERA_ROTATION = 90

class CameraPreviewFragment(
    private val cameraType: Int,
    var cameraFunc: Int
) : Fragment() {

    companion object {
        // 我的手机后面有几个摄像头，如果直接获取面向的方法可能会得到广角的摄像头，我这里默认0为后置，1为前置
        const val REAR_CAMERA = 0
        const val FRONT_CAMERA = 1
        const val VIDEO = 2
        const val PHOTO = 3
    }

    interface OnTakePhotoListener {
        fun onStart()
        fun onCompleted()
        fun onSavePhotoCompleted(bitmap: Bitmap)
    }

    private var binding: FragmentCameraPreviewBinding? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private var takePhotoRequestBuilder: CaptureRequest.Builder? = null
    private lateinit var recordVideoRequestBuilder: CaptureRequest.Builder
    private var cameraId: String = ""
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var backgroundHandlerThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private var mImageReader: ImageReader? = null

    private var onTakePhotoListener: OnTakePhotoListener? = null
    private lateinit var mediaRecorder: MediaRecorder
    private lateinit var fcTextureView:TextureView

    fun setOnTakePhotoListener(listener: OnTakePhotoListener) {
        onTakePhotoListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCameraPreviewBinding.inflate(inflater, container, false)
        startBackgroundThread()
        this.fcTextureView = binding!!.fcTextureView
        return binding?.root
    }

    override fun onResume() {
        super.onResume()
        initImageReader()
        // initMediaRecorder()
        startCameraPreview()
    }

    override fun onPause() {
        super.onPause()
        releaseImageReader()
        releaseMediaRecord()
        releaseCamera()
    }

    private fun releaseImageReader() {
        if (this::mediaRecorder.isInitialized) {
            mediaRecorder.release()
        }
    }

    private fun releaseMediaRecord() {
        if (this::mediaRecorder.isInitialized) {
            mediaRecorder.release()
        }
    }

    private fun initImageReader() {
        //创建图片读取器,参数为分辨率宽度和高度/图片格式/需要缓存几张图片,我这里写的2意思是获取2张照片
        mImageReader = if (cameraType == FRONT_CAMERA) {
            ImageReader.newInstance(ImageSize.FRONT_CAMERA_IMAGE_SIZE.width, ImageSize.FRONT_CAMERA_IMAGE_SIZE.height, ImageFormat.JPEG, 2)
        } else {
            ImageReader.newInstance(ImageSize.BACK_CAMERA_IMAGE_SIZE.width, ImageSize.BACK_CAMERA_IMAGE_SIZE.height, ImageFormat.JPEG, 2)
        }
        mImageReader?.setOnImageAvailableListener({ reader ->
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

    fun enterPhotoModel() {
        if (recording) {
            stopRecordVideo()
        }
        initTakePhotoSessionAndStartPreview()
    }

    fun takePhoto() {
        try {
            if (takePhotoRequestBuilder == null) {
                Log.i("posserTest", "takeoff")
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
            takePhotoRequestBuilder?.let {
                it.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)  //自动对焦
                it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) //自动爆光
                // 这里不做多机适配，只适配了我自己的手机
                val rotation =
                    if (cameraType == FRONT_CAMERA) FRONT_CAMERA_ROTATION else BACK_CAMERA_ROTATION
                it.set(CaptureRequest.JPEG_ORIENTATION, rotation)
                val surface = mImageReader!!.surface
                it.addTarget(surface)
            }

            stopPreview()
            /*
             * CameraCaptureSession.abortCaptures(); //终止获取   尽可能快地放弃当前挂起和正在进行的所有捕获。
             * 这里有一个坑,其实这个并不能随便调用(我是看到别的demo这么使用,但是其实是错误的,所以就在这里备注这个坑).
             * 最好只在Activity里的onDestroy调用它,终止获取是耗时操作,需要一定时间重新打开会话通道.
             * 如果你调用了这个方法关闭了会话又拍照后恢复图像预览,会话就会频繁的开关,
             * 导致拍照图片在处理耗时缓存时你又关闭了会话.导致照片缓存不完整并且失败.
             * 所以切记不要随便使用这个方法,会话开启后并不需要关闭刷新.后续其他拍照/预览/录制视频直接操作这个会话即可
             */
            onTakePhotoListener?.onStart()
            cameraCaptureSession.capture(takePhotoRequestBuilder!!.build(), object :
                CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    // 重启preview视频流
                    restartPreview()
                    onTakePhotoListener?.onCompleted()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            // handle exception
            Toast.makeText(requireActivity(), e.message, Toast.LENGTH_SHORT).show()
        }
    }

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
            setOutputFile(crateMediaMp4File(this@CameraPreviewFragment.requireContext()))
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

    private fun initMediaRecordSessionAndStartPreview() {
        if (!this::recordVideoRequestBuilder.isInitialized) {
            recordVideoRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        }
        val previewSurface = Surface(fcTextureView.surfaceTexture)
        recordVideoRequestBuilder.let {
            it.addTarget(previewSurface)
            it.addTarget(mediaRecorder.surface)
        }

        val captureCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session
                cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), object :
                    CameraCaptureSession.CaptureCallback() {}, backgroundHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                releaseCamera()
            }
        }

        val configurationList = listOf(
            OutputConfiguration(previewSurface),
            OutputConfiguration(mediaRecorder.surface)
        )
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, configurationList, Executors.newFixedThreadPool(5), captureCallback)

        cameraDevice.createCaptureSession(sessionConfiguration)
    }

    fun startOrStopMediaRecord() {
        if (recording) {
            stopRecordVideo()
        } else {
            startRecordVideo()
        }
    }


    fun enterMediaRecordModel() {
        if (cameraFunc != VIDEO) {
            initMediaRecorder()
            mediaRecorder.prepare()
            initMediaRecordSessionAndStartPreview()
        }
    }

    private var recording = false
    private fun startRecordVideo() {
        if (!this::recordVideoRequestBuilder.isInitialized) {
            recordVideoRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        }
        initMediaRecorder()
        mediaRecorder.prepare()
        cameraCaptureSession.setRepeatingRequest(recordVideoRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {}, backgroundHandler)
        recording = true
        mediaRecorder.start()
    }

    private fun stopRecordVideo() {
        stopMediaRecord()
        restartPreview()
    }


    private fun isOpenedCamera(): Boolean {
        return this::cameraManager.isInitialized && this::cameraDevice.isInitialized
    }

    private fun stopPreview() {
        stopRepeating()
    }

    private fun stopMediaRecord() {
        mediaRecorder.stop()
        stopRepeating()
        recording = false
    }

    // 取消任何正在进行的重复捕获集
    private fun stopRepeating() {
        cameraCaptureSession.stopRepeating()
    }

    private fun restartPreview() {
        stopRepeating()
        cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), object :
            CameraCaptureSession.CaptureCallback() {}, backgroundHandler)
    }

    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThread${System.currentTimeMillis()}")
        backgroundHandlerThread.start()
        backgroundHandler = Handler(backgroundHandlerThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread.quitSafely()
        backgroundHandlerThread.join()
    }

    private fun startCameraPreview() {
        Log.i("posserTest", "startCameraPreview")
        // 解决 home 键退出的时候没办法恢复preview问题
        if (binding?.fcTextureView?.surfaceTextureListener != null) {
            initCamera()
        }
        binding?.fcTextureView?.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    Log.i("posserTest", "onSurfaceTextureAvailable")
                    initCamera()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    // 纹理视图大小发生变化，需要更新预览请求
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    releaseCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    // 获取预览帧并将其绘制到纹理上
                }
            }
        cameraManager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private fun initCamera() {
        try {
            cameraId = cameraManager.cameraIdList[cameraType]
            val stateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.i("posserTest", "onOpened")
                    cameraDevice = camera
                    Log.i("posserTest", this@CameraPreviewFragment::cameraDevice.isInitialized.toString())
                    initTakePhotoSessionAndStartPreview()
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

    private fun initTakePhotoSessionAndStartPreview() {
        val previewSurface = Surface(fcTextureView.surfaceTexture)
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(previewSurface)

        val captureCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                cameraCaptureSession = session
                cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), object :
                    CameraCaptureSession.CaptureCallback() {}, backgroundHandler)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                releaseCamera()
            }
        }

        val configurationList = listOf(
            OutputConfiguration(previewSurface),
            OutputConfiguration(mImageReader!!.surface)
        )
        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR, configurationList, Executors.newFixedThreadPool(5), captureCallback)

        cameraDevice.createCaptureSession(sessionConfiguration)
        // cameraDevice.createCaptureSession(listOf(previewSurface), captureCallback, null)
    }


    private fun releaseCamera() {
        cameraDevice.close()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        stopPreview()
        releaseImageReader()
        releaseMediaRecord()
        releaseCamera()
        stopBackgroundThread()
        binding = null
    }
}
