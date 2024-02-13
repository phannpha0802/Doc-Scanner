package kh.com.custom.camera.capture.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kh.com.custom.camera.capture.camera.model.DocStatus
import kh.com.custom.camera.capture.camera.model.MrzHelper
import kh.com.custom.camera.capture.camera.model.TextGraphic
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object CameraUtil {

    fun checkCameraSelfPermission(context: Activity, startCamera: () -> (Unit)) {
        if (allPermissionsGranted(context))
            startCamera()
         else
            ActivityCompat.requestPermissions(context, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    fun checkCameraGranted(context: Activity, requestCode: Int, permissions: Array<String>?, grantResults: IntArray?, startCamera: () -> (Unit)) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted(context)) {
                startCamera()
            } else {
                Toast.makeText(context, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                context.finish()
            }
        }
    }

    private fun allPermissionsGranted(context: Activity) = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)


    // ============================================================================================

    fun cropMediaImage(mediaImage: Image, cropRect: Rect): Bitmap {
        val yBuffer = mediaImage.planes[0].buffer // Y
        val vuBuffer = mediaImage.planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(cropRect, 100, outputStream)
        val imageBytes = outputStream.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun cropBitmapPosition(bitmap: Bitmap, rect: Rect): Bitmap {
        return Bitmap.createBitmap(bitmap,rect.left, rect.top, rect.width(), rect.height())
    }

    fun rotateBitmap(img: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }

    // ============================================================================================

    fun cropBitmapByShapeOverlay(bitmap: Bitmap, viewFinder: View, shapeOverlay: View): Bitmap {
        val ratioX = bitmap.width.toFloat() / viewFinder.width.toFloat()
        val ratioY = bitmap.height.toFloat() / viewFinder.height.toFloat()

        val leftFrame: Int = shapeOverlay.left
        val topFrame: Int = shapeOverlay.top

        val x = (leftFrame * ratioX).roundToInt() + (shapeOverlay.width * 0.1).toInt()
        val y = (topFrame * ratioY).roundToInt()

        val width = shapeOverlay.width * bitmap.width / viewFinder.width - (shapeOverlay.width * 0.22).toInt()
        val height = shapeOverlay.height * bitmap.height / viewFinder.height

        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    fun cropText(bitmap: Bitmap, viewFinder: View, shapeOverlay: View): Bitmap {
        val ratioX = bitmap.width.toFloat() / viewFinder.width.toFloat()
        val ratioY = bitmap.height.toFloat() / viewFinder.height.toFloat()

        val leftFrame: Int = shapeOverlay.left
        val topFrame: Int = shapeOverlay.top

        val x = (leftFrame * ratioX).roundToInt() + (shapeOverlay.width * 0.1).toInt()
        val y = (topFrame * ratioY).roundToInt() + (shapeOverlay.height * 0.26).toInt()

        val width = shapeOverlay.width * bitmap.width / viewFinder.width - (shapeOverlay.width * 0.22).toInt()
        val height = shapeOverlay.height * bitmap.height / viewFinder.height - (shapeOverlay.height * 0.26).toInt()

        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    // ===========================================================================================

    @SuppressLint("SetTextI18n")
    fun verifyDocFromImage(bitmap: Bitmap, graphic: TextGraphic, docStatus: (DocStatus) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        textRecognizer.process(image)
            .addOnSuccessListener {
                val listTextBlock = it.textBlocks
                graphic.setTextBlocks(listTextBlock, bitmap.width.toFloat(), bitmap.height.toFloat(), true)
                val text = stripWhiteSpace(getText(listTextBlock))
                val mrzKey = MrzHelper()
                val mrz = mrzKey.process(text)
                if (mrz?.getMRZType() == "TD1" || mrz?.getMRZType() == "TD3"){
                    docStatus(DocStatus.VALID)
                    textRecognizer.close()
                } else {
                    docStatus(DocStatus.INVALID)
                    return@addOnSuccessListener
                }
            }
            .addOnFailureListener {
                docStatus(DocStatus.INVALID)
                return@addOnFailureListener
            }
    }

    private fun stripWhiteSpace(mrzKey: String): String {
        var fixedString = mrzKey.replace("\n", "")
        fixedString = fixedString.replace(" ", "")
        fixedString = fixedString.replace("\t", "")
        fixedString = fixedString.replace("«", "<")
        return fixedString
    }

    private fun getText(listBlock: List<Text.TextBlock>): String {
        var textResult = ""
        for (block in listBlock) {
            textResult += "\t\t"
            for (line in block.lines) {
                textResult += line.text
            }
            textResult += "\n\n"
        }

        return textResult
    }
}