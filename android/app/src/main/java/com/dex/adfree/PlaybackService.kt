package com.dex.adfree

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Keeps the app process alive (with a notification) while music plays in the background
 * or downloads are running, and stops itself when neither is happening.
 */
class PlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private var watchMusic = false

    private val check = object : Runnable {
        override fun run() {
            val music = watchMusic && audio().isMusicActive
            if (!music) watchMusic = false
            if (!music && Downloader.ACTIVE.get() == 0) {
                stopSelf()
                return
            }
            notify(music)
            handler.postDelayed(this, 4000)
        }
    }

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("music", false) == true) watchMusic = true
        val music = watchMusic
        try {
            val n = build(music)
            if (Build.VERSION.SDK_INT >= 29) {
                val type = if (music) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                startForeground(ID, n, type)
            } else {
                startForeground(ID, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "adfree:keepalive").apply { acquire(6 * 60 * 60 * 1000L) }
        }
        handler.removeCallbacks(check)
        handler.postDelayed(check, 8000)
        return START_NOT_STICKY
    }

    private fun build(music: Boolean): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background activity", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Downloader.ACTIVE.get()
        val text = when {
            music && n > 0 -> "Playing audio · $n download(s) running"
            music -> "Playing in background"
            else -> "$n download(s) running"
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Sonic")
            .setContentText(text)
            .setSmallIcon(if (music) android.R.drawable.ic_media_play else android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notify(music: Boolean) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, build(music))
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        wake = null
        super.onDestroy()
    }

    private companion object {
        const val ID = 41
        const val CHANNEL = "background"
    }
}
