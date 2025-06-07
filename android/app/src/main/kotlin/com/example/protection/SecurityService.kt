package com.example.protection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.hardware.camera2.*
import android.location.LocationManager
import android.location.LocationListener
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import android.util.Log
import android.media.ImageReader
import android.graphics.ImageFormat
import android.location.Location
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.mail.*
import javax.mail.internet.*
import java.util.Properties
import kotlin.concurrent.thread
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.core.content.ContextCompat


class SecurityService : Service() {
    private lateinit var bootReceiver: BroadcastReceiver
    private lateinit var lockScreenReceiver: BroadcastReceiver
    private var failedAttempts = 0
    private lateinit var cameraManager: CameraManager
    private lateinit var imageReader: ImageReader
    private lateinit var cameraDevice: CameraDevice
    private lateinit var locationManager: LocationManager
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var powerManager: PowerManager
    private var currentLocation: Location? = null
    private val CHANNEL_ID = "SecurityServiceChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        setupLocationUpdates()
        registerReceivers()
    }

    private fun registerReceivers() {
        // Receiver pour le démarrage du téléphone
        bootReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                    // Le service est déjà démarré, pas besoin de le redémarrer
                }
            }
        }

        // Receiver pour la détection du verrouillage
        lockScreenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        if (keyguardManager.isKeyguardLocked) {
                            // L'écran est allumé et verrouillé
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // Déverrouillage réussi
                        failedAttempts = 0
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        // Potentielle tentative échouée
                        failedAttempts++
                        if (failedAttempts == 1) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                takeSilentPhoto()
                            }, 1000)
                        }
                    }
                }
            }
        }

        // Enregistrer les receivers
        val bootFilter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)
        registerReceiver(bootReceiver, bootFilter)

        val lockScreenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(lockScreenReceiver, lockScreenFilter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service de Sécurité",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service de surveillance du déverrouillage"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Service de Protection")
            .setContentText("Surveillance active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupLocationUpdates() {
        try {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = location
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                10f,
                locationListener
            )
        } catch (e: SecurityException) {
            Log.e("SecurityService", "Erreur permission localisation: ${e.message}")
        }
    }

    private fun takeSilentPhoto() {
        try {
            val cameraId = cameraManager.cameraIdList.first { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            }

            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    
                    val file = createImageFile()
                    FileOutputStream(file).use { output ->
                        output.write(bytes)
                    }
                    
                    currentLocation?.let { location ->
                        sendEmail(file.absolutePath, "${location.latitude}, ${location.longitude}")
                    }
                } finally {
                    image.close()
                }
            }, null)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                        }.build()

                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    session.capture(captureRequest, null, null)
                                }
                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e("SecurityService", "Échec de la configuration de la session")
                                }
                            },
                            null
                        )
                    } catch (e: CameraAccessException) {
                        Log.e("SecurityService", "Erreur accès caméra: ${e.message}")
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    Log.e("SecurityService", "Erreur caméra: $error")
                }
            }, null)
        } catch (e: Exception) {
            Log.e("SecurityService", "Erreur lors de la prise de photo: ${e.message}")
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "PHOTO_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun sendEmail(photoPath: String, location: String) {
        thread {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.socketFactory.port", "465")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.port", "465")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication("roosevelt.tbr@gmail.com", "tbr2005tbR@")
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress("roosevelt.tbr@gmail.com"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse("roosevelt.tbr@gmail.com"))
                    subject = "Tentative de déverrouillage échouée"
                    
                    val multipart = MimeMultipart().apply {
                        addBodyPart(MimeBodyPart().apply {
                            setText("Localisation: $location")
                        })
                        addBodyPart(MimeBodyPart().apply {
                            setFileName("photo.jpg")
                            setDataHandler(DataHandler(FileDataSource(photoPath)))
                        })
                    }
                    setContent(multipart)
                }

                Transport.send(message)
                
                // Supprimer la photo après l'envoi
                File(photoPath).delete()
            } catch (e: Exception) {
                Log.e("SecurityService", "Erreur lors de l'envoi de l'email: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bootReceiver)
        unregisterReceiver(lockScreenReceiver)
        if (::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
    }
}

