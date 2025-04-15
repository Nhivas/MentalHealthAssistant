package com.example.mental_ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat

class DoomscrollAccessibilityService : AccessibilityService() {
    private var swipeCount = 0
    private var lastSwipeTime = 0L
    private var timerMinutes: Int = 30
    private var swipeInactivityHandler = Handler(Looper.getMainLooper())
    private var swipeInactivityRunnable: Runnable? = null
    private val SWIPE_INACTIVITY_LIMIT = 30_000L // 30 seconds
    private var countDownTimer: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager
    private var currentApp: String? = null

    companion object {
        const val CHANNEL_ID = "DoomScrollChannel"
        const val NOTIFICATION_ID = 1
        var isTimerRunning = false

        // Action constants for broadcasts
        const val ACTION_UPDATE_TIMER = "com.example.mental_ai.ACTION_UPDATE_TIMER"
        const val ACTION_STOP_MONITORING = "com.example.mental_ai.ACTION_STOP_MONITORING"
    }

    private var appOpenedTime: Long = 0L
    private var isMonitoringActive = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_TIMER -> {
                    val newTimerMinutes = intent.getIntExtra("timer_minutes", 30)
                    timerMinutes = newTimerMinutes
                    Log.d("DoomScroll", "Timer updated to $timerMinutes minutes")

                    // Reset timer if it's running
                    if (isTimerRunning) {
                        resetTimer()
                        // If we're actively monitoring, restart the timer with new value
                        if (isMonitoringActive && swipeCount >= 80) {
                            startTimer()
                        }
                    }

                    isMonitoringActive = true
                }
                ACTION_STOP_MONITORING -> {
                    isMonitoringActive = false
                    resetTimer()
                    Log.d("DoomScroll", "Monitoring stopped via broadcast")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Register broadcast receiver
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATE_TIMER)
            addAction(ACTION_STOP_MONITORING)
        }
        registerReceiver(broadcastReceiver, intentFilter)

        // Get initial values
        val prefs = getSharedPreferences("doomscroll_prefs", Context.MODE_PRIVATE)
        timerMinutes = prefs.getInt("timer_minutes", 30)
        isMonitoringActive = prefs.getBoolean("is_monitoring_active", false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isMonitoringActive || event == null || event.packageName == null) return

        val packageName = event.packageName.toString()
        val validPackages = listOf("com.instagram.android", "com.google.android.youtube")

        // Ignore your own app and system UI
        if (packageName.startsWith("com.example.") || packageName.startsWith("com.android.systemui")) {
            return
        }

        val eventType = event.eventType

        // Detect app switches
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName != currentApp) {
                currentApp = packageName

                if (currentApp in validPackages) {
                    appOpenedTime = System.currentTimeMillis()
                    Log.d("DoomScroll", "Opened $currentApp at $appOpenedTime")
                } else {
                    if (isTimerRunning && currentApp in validPackages) {
                        Log.d("DoomScroll", "App switched from $currentApp to $packageName. Resetting.")
                        resetTimer()
                    }
                    currentApp = null
                }
            }
        }

        if (packageName in validPackages &&
            (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ) {
            val currentTime = System.currentTimeMillis()

            // Only start counting swipes after 10s in the target app
            if (currentTime - appOpenedTime < 10_000) return

            if (currentTime - lastSwipeTime <= 60_000) {
                swipeCount++
            } else {
                swipeCount = 1
            }

            lastSwipeTime = currentTime
            Log.d("DoomScroll", "Swipe count: $swipeCount in 60 sec")

            // Reset the 30s inactivity timer
            swipeInactivityRunnable?.let { swipeInactivityHandler.removeCallbacks(it) }
            swipeInactivityRunnable = Runnable {
                Log.d("DoomScroll", "No swipes for 30 seconds. Timer reset.")
                resetTimer()
                swipeCount = 0
            }
            swipeInactivityHandler.postDelayed(swipeInactivityRunnable!!, SWIPE_INACTIVITY_LIMIT)

            if (swipeCount >= 80 && !isTimerRunning && isMonitoringActive) {
                Log.d("DoomScroll", "Detected 80+ scrolls. Starting timer.")
                showNotification("User has started scrolling")
                startTimer()
            }
        }
    }

    private fun startTimer() {
        isTimerRunning = true
        showNotification("DoomScroll Timer Started!")
        Toast.makeText(this, "User has started scrolling", Toast.LENGTH_SHORT).show()

        countDownTimer = object : CountDownTimer((timerMinutes * 60 * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 60000).toInt()
                val seconds = ((millisUntilFinished % 60000) / 1000).toInt()
                updateNotification("Time remaining: $minutes min $seconds sec")
            }

            override fun onFinish() {
                triggerAlert()
                resetTimer()
            }
        }.start()
    }

    private fun triggerAlert() {
        handler.post {
            Toast.makeText(this, "STOP SCROLLING! Time's up.", Toast.LENGTH_LONG).show()

            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        isTimerRunning = false
        swipeCount = 0
        lastSwipeTime = 0L

        swipeInactivityRunnable?.let {
            swipeInactivityHandler.removeCallbacks(it)
            swipeInactivityRunnable = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d("DoomScroll", "Timer reset.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DoomScroll Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(text: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DoomScroll Guard")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DoomScroll Guard")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onInterrupt() {
        Toast.makeText(this, "DoomScroll service interrupted", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        resetTimer()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Log.e("DoomScroll", "Error unregistering receiver: ${e.message}")
        }
    }
}