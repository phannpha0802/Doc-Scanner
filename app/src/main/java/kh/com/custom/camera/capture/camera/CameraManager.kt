package kh.com.custom.camera.capture.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.view.Surface
import android.view.View
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.google.mlkit.vision.common.InputImage
import kh.com.custom.camera.capture.camera.model.DocStatus
import kh.com.custom.camera.capture.camera.model.TextGraphic
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(context: Context, viewModelOwner: ViewModelStoreOwner, lifecycleOwner: LifecycleOwner, viewFinder: PreviewView, shapeOverlay: View, graphic: TextGraphic) {

    private var context: Context
    private var controller: CameraController
    private val _controller get() = controller

    var bitmap = MutableLiveData<Bitmap>()

    var status = MutableLiveData<DocStatus>()

    private var lifecycleOwner : LifecycleOwner
    private val _lifecycleOwner get() = lifecycleOwner

    private var viewFinder: PreviewView
    private val _viewFinder get() = viewFinder

    private var shapeOverlay: View
    private val _shapeOverlay get() = shapeOverlay

    private var graphic: TextGraphic
    private val _graphic get() = graphic

    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelectorOption = CameraSelector.LENS_FACING_FRONT
    private var imageCapture: ImageCapture? = null
    private lateinit var imageAnalyzer: ImageAnalysis

    init {
        this.context = context
        this.lifecycleOwner = lifecycleOwner
        this.viewFinder = viewFinder
        this.shapeOverlay = shapeOverlay
        this.graphic = graphic
        controller = ViewModelProvider(viewModelOwner)[CameraController::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @OptIn(ExperimentalGetImage::class)
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder()
                    .setTargetRotation(Surface.ROTATION_0)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraSelectorOption)
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && mediaImage.format == ImageFormat.YUV_420_888) {
                                CameraUtil.cropMediaImage(mediaImage, imageProxy.cropRect)
                                    .let { bitmap ->
                                        val image = CameraUtil.cropBitmapPosition(bitmap, imageProxy.cropRect)
                                        val imgAfterRotated = CameraUtil.rotateBitmap(image, 90)
                                        val imgAfterCropped = CameraUtil.cropBitmapByShapeOverlay(imgAfterRotated, _viewFinder, _shapeOverlay)
                                        val cropText = CameraUtil.cropText(imgAfterRotated, _viewFinder, _shapeOverlay)
                                        this.bitmap.postValue(imgAfterCropped)
                                        CameraUtil.verifyDocFromImage(cropText, _graphic){ status ->
                                            this.status.postValue(status)
                                            imageProxy.close()
                                        }
                                        imageProxy.close()
                                    }
                            }
                        }
                    }

                setCameraConfig(cameraProvider, cameraSelector)
            }, ContextCompat.getMainExecutor(context)
        )
    }

    private fun setCameraConfig(cameraProvider: ProcessCameraProvider?, cameraSelector: CameraSelector) {
        try {
            cameraProvider?.unbindAll()
            camera?.cameraControl?.enableTorch(true)
            camera = cameraProvider?.bindToLifecycle(_lifecycleOwner, cameraSelector, preview, imageAnalyzer, imageCapture)
            preview?.setSurfaceProvider(_viewFinder.surfaceProvider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun changeCameraSelector() {
        cameraProvider?.unbindAll()
        cameraSelectorOption = if (cameraSelectorOption == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    }

}