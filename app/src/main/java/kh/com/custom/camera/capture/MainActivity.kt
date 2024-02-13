package kh.com.custom.camera.capture

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import kh.com.custom.camera.capture.camera.CameraManager
import kh.com.custom.camera.capture.camera.CameraUtil
import kh.com.custom.camera.capture.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val _binding get() = binding

    private lateinit var cameraManager: CameraManager
    private val _cameraManager get() = cameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        initCamera()

        animationView()
    }

    private fun animationView() {

        _binding.apply {
            _cameraManager.bitmap.observe(this@MainActivity){
                ivPreview.setImageBitmap(it)
            }

            _cameraManager.status.observe(this@MainActivity){
                tvStatus.text = it.name
            }
        }
    }

    private fun initCamera() {
        cameraManager = CameraManager(this, this, this, _binding.viewFinder, _binding.shapeOverlay, _binding.graphic)
        cameraManager.changeCameraSelector()
        CameraUtil.checkCameraSelfPermission(this, ::startCM)
    }

    private fun startCM() {
        _cameraManager.startCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CameraUtil.checkCameraGranted(this,requestCode,permissions,grantResults, ::startCM)
    }
}