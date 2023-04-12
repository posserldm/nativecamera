package posser.nativecamera

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import posser.nativecamera.databinding.ActivityMainBinding
import posser.nativecamera.util.checkSelfPermissionCompat
import posser.nativecamera.util.requestPermissionsCompat


private const val REQUEST_PERMISSION_CODE = 101

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestPermissions()
        testCode()
    }

    private fun requestPermissions() {
        val progressions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        for (p in progressions) {
            if (checkSelfPermissionCompat(p) != PackageManager.PERMISSION_GRANTED)  {
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
                    Toast.makeText(this, "请前往设置->应用->权限管理->打开存储权限、读写权限、相机权限，否则无法使用相关功能", Toast.LENGTH_LONG).show()
                    break
                }
            }
        }
    }

    private fun testCode() {

    }
}