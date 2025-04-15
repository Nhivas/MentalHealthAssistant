package com.example.mental_ai

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var timerInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        timerInput = findViewById(R.id.timerInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)

        // Set previously saved timer value (optional)
        val prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        val savedTime = prefs.getInt("timer_minutes", 30)
        timerInput.setText(savedTime.toString())

        // Update status text based on current state
        updateStatusText()

        // Usage Stats permission
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        }

        startButton.setOnClickListener {
            val minutes = timerInput.text.toString().toIntOrNull()
            if (minutes == null || minutes <= 0) {
                Toast.makeText(this, "Please enter a valid time in minutes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkAccessibilityPermission(minutes)
        }

        // Stop button logic
        stopButton.setOnClickListener {
            stopDoomscrollService()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update status text when returning to the app
        updateStatusText()
    }

    private fun updateStatusText() {
        val prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_monitoring_active", false)
        val minutes = prefs.getInt("timer_minutes", 30)

        if (isActive) {
            statusText.text = "Status: Active ($minutes min)"
        } else {
            statusText.text = "Status: Inactive"
        }
    }

    private fun checkAccessibilityPermission(minutes: Int) {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityDialog(minutes)
        } else {
            startDoomscrollService(minutes)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle("Usage Access Required")
            .setMessage("To detect which app is being used, allow Usage Access for DoomScroll Guard.")
            .setPositiveButton("Grant Access") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, DoomscrollAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponent) == true
    }

    private fun showAccessibilityDialog(minutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("To detect swipes in other apps, please enable DoomScroll Guard in accessibility settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Return here after enabling the service", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDoomscrollService(minutes: Int) {
        val prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("timer_minutes", minutes)
            .putBoolean("is_monitoring_active", true)
            .apply()

        // Send broadcast to update the service with new timer value
        val intent = Intent(DoomscrollAccessibilityService.ACTION_UPDATE_TIMER)
        intent.putExtra("timer_minutes", minutes)
        sendBroadcast(intent)

        statusText.text = "Status: Active ($minutes min)"
        Toast.makeText(this, "Monitoring for doomscrolling ($minutes min limit)", Toast.LENGTH_SHORT).show()
    }

    private fun stopDoomscrollService() {
        // Update shared preferences to indicate monitoring is stopped
        val prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_monitoring_active", false)
            .apply()

        // Send broadcast to stop the service
        val intent = Intent(DoomscrollAccessibilityService.ACTION_STOP_MONITORING)
        sendBroadcast(intent)

        // Update the status text
        statusText.text = "Status: Inactive"

        // Show a toast notification
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }
}