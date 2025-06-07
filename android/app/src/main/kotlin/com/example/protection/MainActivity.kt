package com.example.protection

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.content.SharedPreferences
import android.content.Context
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.util.Log
import android.hardware.camera2.CameraCharacteristics

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.protection/security"
    private val PERMISSIONS_REQUEST_CODE = 123
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
        Manifest.permission.FOREGROUND_SERVICE
    )

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkPermissions" -> {
                    if (checkPermissions()) {
                        result.success(true)
                    } else {
                        requestPermissions()
                        result.success(false)
                    }
                }
                "startSecurityService" -> {
                    startSecurityService()
                    result.success(true)
                }
                "takePhoto" -> {
                    if (checkPermissions()) {
                        takePhoto { success, path ->
                            result.success(mapOf("success" to success, "path" to path))
                        }
                    } else {
                        result.error("PERMISSION_DENIED", "Permissions not granted", null)
                    }
                }
                "getLocation" -> {
                    if (checkPermissions()) {
                        getLocation { success, location ->
                            result.success(mapOf("success" to success, "location" to location))
                        }
                    } else {
                        result.error("PERMISSION_DENIED", "Permissions not granted", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAutoStartPermission()
    }

    override fun onResume() {
        super.onResume()
        startSecurityService()
    }

    private fun startSecurityService() {
        val serviceIntent = Intent(this, SecurityService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun checkAutoStartPermission() {
        val prefs: SharedPreferences = getSharedPreferences("protection_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("auto_start_shown", false)) {
            startActivity(Intent(this, AutoStartPermissionActivity::class.java))
            prefs.edit().putBoolean("auto_start_shown", true).apply()
        }
    }

    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    private fun takePhoto(callback: (Boolean, String?) -> Unit) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.first { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }

            // La logique de prise de photo est gérée par SecurityAction
            callback(true, null)
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur lors de la prise de photo: ${e.message}")
            callback(false, null)
        }
    }

    private fun getLocation(callback: (Boolean, String?) -> Unit) {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location != null) {
                callback(true, "${location.latitude}, ${location.longitude}")
            } else {
                callback(false, null)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur lors de l'obtention de la localisation: ${e.message}")
            callback(false, null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Toutes les permissions sont accordées
                startSecurityService()
            }
        }
    }
}
