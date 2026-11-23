package com.sapos.caller

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var swCaller: SwitchCompat
    private lateinit var swWa: SwitchCompat
    private lateinit var tvStatus: TextView
    private lateinit var tvLogs: TextView

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            tvLogs.text = SaPosClient.getLogsText()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        swCaller = findViewById(R.id.swCaller)
        swWa = findViewById(R.id.swWa)
        tvStatus = findViewById(R.id.tvStatus)
        tvLogs = findViewById(R.id.tvLogs)

        etHost.setText(SaPosClient.getHost(this))
        etPort.setText(SaPosClient.getPort(this))
        swCaller.isChecked = SaPosClient.isCallerEnabled(this)
        swWa.isChecked = SaPosClient.isWaEnabled(this)
        tvLogs.text = SaPosClient.getLogsText()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveAndRestartBridge()
            Toast.makeText(this, "Ayarlar kaydedildi ve köprü başlatıldı.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTestConn).setOnClickListener {
            saveAndRestartBridge()
            tvStatus.text = "Kasa test ediliyor..."
            SaPosClient.testStatus(this) { ok, msg ->
                runOnUiThread {
                    tvStatus.text = if (ok) "BAĞLI: ${SaPosClient.getBaseUrl(this)}" else "BAĞLANTI YOK: $msg"
                }
            }
        }

        findViewById<Button>(R.id.btnPermPhone).setOnClickListener {
            requestRuntimePermissions()
        }

        findViewById<Button>(R.id.btnPermNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } else {
                Toast.makeText(this, "Pil kısıtlaması zaten kapalı.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnTestCall).setOnClickListener {
            saveAndRestartBridge()
            SaPosClient.sendCallerId(this, "05324189210") { ok, _ ->
                runOnUiThread {
                    Toast.makeText(this, if (ok) "Test araması kasaya iletildi!" else "Kasaya ulaşılamadı!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnTestWa).setOnClickListener {
            saveAndRestartBridge()
            SaPosClient.sendWhatsApp(this, "Test Müşteri", "2 et döner dürüm 2 ayran") { ok, _ ->
                runOnUiThread {
                    Toast.makeText(this, if (ok) "Test WhatsApp siparişi iletildi!" else "Kasaya ulaşılamadı!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        startKeepAlive()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SaPosClient.ACTION_LOG_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
        tvLogs.text = SaPosClient.getLogsText()
        updatePermissionStatus()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(logReceiver) } catch (_: Exception) {}
    }

    private fun saveAndRestartBridge() {
        SaPosClient.saveSettings(
            this,
            etHost.text.toString(),
            etPort.text.toString(),
            swCaller.isChecked,
            swWa.isChecked
        )
        startKeepAlive()
    }

    private fun startKeepAlive() {
        val svc = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 200)
    }

    private fun updatePermissionStatus() {
        val phoneOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val notifOk = enabledListeners.contains(packageName)

        tvStatus.text = "Telefon İzni: ${if (phoneOk) "OK" else "EKSİK"} | Bildirim İzni: ${if (notifOk) "OK" else "EKSİK"}"
    }
}
