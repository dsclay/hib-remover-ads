package com.dex.adfree

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Lets the service run JavaScript in the page that is playing (set by MainActivity). Callbacks arrive on the main thread. */
object PlaybackBridge {
    @Volatile var evaluate: ((String, (String) -> Unit) -> Unit)? = null
}

/**
 * Keeps the app alive while music plays in the background or downloads run, and shows a media
 * notification (title, artist, artwork, previous / play-pause / next) for the page that is playing.
 */
class PlaybackService : Service() {
    private class Info(val title: String, val artist: String, val img: String, val playing: Boolean, val durationMs: Long, val positionMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var wake: PowerManager.WakeLock? = null
    private var session: MediaSession? = null
    private var watchMusic = false
    private var lastActive = 0L
    private var info: Info? = null
    private var art: Bitmap? = null
    private var artUrl = ""

    private val check = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val active = audio().isMusicActive
            if (active) lastActive = now
            val music = watchMusic && (active || now - lastActive < GRACE_MS) // paused from the notification stays for a while
            if (!music) watchMusic = false
            if (!music && Downloader.ACTIVE.get() == 0) {
                stopSelf()
                return
            }
            if (music) poll { refresh(true) } else refresh(false)
            handler.postDelayed(this, if (music) 2000 else 4000)
        }
    }

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "hedgehog").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = js(PLAY)
                override fun onPause() = js(PAUSE)
                override fun onSkipToNext() = js(NEXT)
                override fun onSkipToPrevious() = js(PREV)
                override fun onSeekTo(pos: Long) = js("(function(){var v=document.querySelector('video');if(v)v.currentTime=${pos / 1000.0}})()")
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> { js(PREV); return START_NOT_STICKY }
            ACTION_TOGGLE -> { js(TOGGLE); return START_NOT_STICKY }
            ACTION_NEXT -> { js(NEXT); return START_NOT_STICKY }
        }
        if (intent?.getBooleanExtra("music", false) == true) {
            watchMusic = true
            lastActive = System.currentTimeMillis()
        }
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
        handler.postDelayed(check, 1500)
        return START_NOT_STICKY
    }

    private fun js(code: String) {
        PlaybackBridge.evaluate?.invoke(code) { }
    }

    /** Reads what the page is playing (YouTube fills navigator.mediaSession with title, artist and artwork). */
    private fun poll(done: () -> Unit) {
        val bridge = PlaybackBridge.evaluate
        if (bridge == null) { done(); return }
        bridge(QUERY) { raw ->
            runCatching {
                val s = JSONTokener(raw).nextValue() as? String
                if (s != null) {
                    val o = JSONObject(s)
                    info = Info(
                        o.optString("t"), o.optString("a"), o.optString("img"), o.optBoolean("p"),
                        (o.optDouble("d", 0.0).takeIf { it.isFinite() } ?: 0.0).times(1000).toLong(),
                        (o.optDouble("c", 0.0).takeIf { it.isFinite() } ?: 0.0).times(1000).toLong(),
                    )
                    loadArt(o.optString("img"))
                }
            }
            done()
        }
    }

    private fun loadArt(url: String) {
        if (!url.startsWith("https://") || url == artUrl) return
        artUrl = url
        io.execute {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
                c.disconnect()
                if (bmp != null) art = bmp
            } catch (_: Exception) {
            }
        }
    }

    private fun refresh(music: Boolean) {
        val i = info
        if (music && i != null) {
            session?.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, i.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, i.artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, i.durationMs)
                    .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                    .build(),
            )
            session?.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO,
                    )
                    .setState(if (i.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, i.positionMs, if (i.playing) 1f else 0f)
                    .build(),
            )
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, build(music))
    }

    private fun action(icon: Int, label: String, act: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, act.hashCode(), Intent(this, PlaybackService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun build(music: Boolean): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background activity", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Downloader.ACTIVE.get()
        val i = info
        val b = Notification.Builder(this, CHANNEL)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(if (music) android.R.drawable.ic_media_play else android.R.drawable.stat_sys_download)
        if (music && i != null) {
            b.setContentTitle(i.title.ifBlank { "Hedgehog" })
                .setContentText(if (n > 0) "${i.artist} · $n download(s)" else i.artist)
                .setLargeIcon(art)
                .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
                .addAction(action(if (i.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play/Pause", ACTION_TOGGLE))
                .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
                .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        } else {
            b.setContentTitle("Hedgehog")
                .setContentText(
                    when {
                        music && n > 0 -> "Playing audio · $n download(s) running"
                        music -> "Playing in background"
                        else -> "$n download(s) running"
                    },
                )
        }
        return b.build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        wake = null
        session?.release()
        session = null
        io.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val ID = 41
        const val CHANNEL = "background"
        const val GRACE_MS = 5 * 60 * 1000L
        const val ACTION_PREV = "com.dex.adfree.PREV"
        const val ACTION_TOGGLE = "com.dex.adfree.TOGGLE"
        const val ACTION_NEXT = "com.dex.adfree.NEXT"

        const val QUERY =
            "(function(){var v=document.querySelector('video');var m=navigator.mediaSession&&navigator.mediaSession.metadata;" +
                "var a=m&&m.artwork&&m.artwork.length?m.artwork[m.artwork.length-1].src:'';" +
                "return JSON.stringify({t:m?m.title:document.title,a:m?m.artist:'',img:a,p:v?!v.paused:false," +
                "d:v?v.duration:0,c:v?v.currentTime:0})})()"
        const val PLAY = "(function(){var v=document.querySelector('video');if(v)v.play()})()"
        const val PAUSE = "(function(){var v=document.querySelector('video');if(v)v.pause()})()"
        const val TOGGLE = "(function(){var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause()}})()"
        const val NEXT =
            "(function(){var b=document.querySelector('.ytp-next-button,button[aria-label*=\"Next\"]');var v=document.querySelector('video');" +
                "if(b)b.click();else if(v&&v.duration)v.currentTime=v.duration})()"
        const val PREV =
            "(function(){var v=document.querySelector('video');if(v&&v.currentTime>4){v.currentTime=0;return}" +
                "var b=document.querySelector('.ytp-prev-button,button[aria-label*=\"Previous\"]');if(b)b.click();else if(v)v.currentTime=0})()"
    }
}
