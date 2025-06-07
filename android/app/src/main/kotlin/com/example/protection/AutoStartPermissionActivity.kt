package com.example.protection

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AutoStartPermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_start_permission)

        findViewById<TextView>(R.id.titleText).text = "Autorisation de démarrage automatique"
        findViewById<TextView>(R.id.messageText).text = "Pour que l'application fonctionne correctement, veuillez autoriser le démarrage automatique."

        findViewById<Button>(R.id.grantButton).setOnClickListener {
            try {
                val intent = Intent()
                val manufacturer = android.os.Build.MANUFACTURER.lowercase()
                when {
                    manufacturer.contains("xiaomi") -> {
                        intent.component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                    manufacturer.contains("oppo") -> {
                        intent.component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                    manufacturer.contains("vivo") -> {
                        intent.component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                    manufacturer.contains("huawei") -> {
                        intent.component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                    }
                    else -> {
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.data = Uri.fromParts("package", packageName, null)
                    }
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.skipButton).setOnClickListener {
            finish()
        }
    }
} 