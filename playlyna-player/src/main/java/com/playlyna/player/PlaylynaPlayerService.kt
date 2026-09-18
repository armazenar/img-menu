package com.playlyna.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import java.util.Collections

class PlaylynaPlayerService : Service() {

    data class TrackInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val uri: String,
        val durationMs: Long
    )

    private val binder = LocalBinder()
    private lateinit var mediaSession: MediaSessionCompat
    private val handler = Handler(Looper.getMainLooper())

    // Estado da musica atual (sincronizado pelo HTML via setMetadata/setPosition)
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentAlbum: String = ""
    private var currentDurationMs: Long = 0L
    private var currentPositionMs: Long = 0L
    private var currentIsPlaying: Boolean = false

    private var shuffle = false
    private var repeatMode = "off"
    private var listener: ((String, Map<String, Any>?) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaylynaPlayerService = this@PlaylynaPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "PlaylynaSession")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // O HTML e quem manda na reproducao - avisa ele
                emit("action", mapOf("action" to "play"))
            }
            override fun onPause() {
                emit("action", mapOf("action" to "pause"))
            }
            override fun onSkipToNext() {
                emit("action", mapOf("action" to "next"))
            }
            override fun onSkipToPrevious() {
                emit("action", mapOf("action" to "prev"))
            }
            override fun onSeekTo(pos: Long) {
                emit("action", mapOf("action" to "seek", "ms" to pos))
            }
        })
        mediaSession.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post { try { startFg() } catch (_: Exception) {} }
        return START_STICKY
    }

    fun setListener(l: (String, Map<String, Any>?) -> Unit) { listener = l }
    private fun emit(event: String, data: Map<String, Any>?) { handler.post { listener?.invoke(event, data) } }

    // ============ ESTADO SINCRONIZADO PELO HTML ============
    fun setMetadata(title: String, artist: String, album: String, durationMs: Long, isPlaying: Boolean) {
        handler.post {
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentDurationMs = durationMs
            currentIsPlaying = isPlaying
            updateNotification()
            updatePlaybackState()
        }
    }

    fun setPosition(positionMs: Long, isPlaying: Boolean) {
        handler.post {
            currentPositionMs = positionMs
            currentIsPlaying = isPlaying
            updatePlaybackState()
        }
    }

    // ============ CONTROLES VINDOS DO APP (so pra notificacao) ============
    fun play() { handler.post { currentIsPlaying = true; updateNotification(); updatePlaybackState() } }
    fun pause() { handler.post { currentIsPlaying = false; updateNotification(); updatePlaybackState() } }
    fun toggle() { handler.post { currentIsPlaying = !currentIsPlaying; updateNotification(); updatePlaybackState() } }
    fun next() { emit("action", mapOf("action" to "next")) }
    fun prev() { emit("action", mapOf("action" to "prev")) }
    fun notifyNext() { emit("action", mapOf("action" to "next")) }
    fun notifyPrev() { emit("action", mapOf("action" to "prev")) }
    fun notifySeek(ms: Long) { emit("action", mapOf("action" to "seek", "ms" to ms)) }

    fun setQueue(tracks: List<TrackInfo>, startIndex: Int) {
        // Nao faz nada - so o HTML e dono do queue
    }

    fun setShuffle(on: Boolean) { handler.post { shuffle = on } }
    fun setRepeatMode(m: String) { handler.post { repeatMode = m } }
    fun isPlaying(): Boolean = currentIsPlaying
    fun getPosition(): Long = currentPositionMs
    fun getDuration(): Long = currentDurationMs
    fun getCurrentIndex(): Int = 0

    // ============ NOTIFICACAO ============
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Reproducao", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(): Notification {
        val openPi = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playIcon = if (currentIsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = if (currentTitle.isNotEmpty()) currentTitle else "Playlyna"
        val subtitle = currentArtist.ifEmpty { "Tocando agora" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(currentIsPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", null)
            .addAction(playIcon, "Play/Pause", null)
            .addAction(android.R.drawable.ic_media_next, "Proxima", null)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun startFg() {
        val n = buildNotif()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotif())
        } catch (_: Exception) {}
    }

    private fun updatePlaybackState() {
        val t = if (currentTitle.isNotEmpty()) currentTitle else "Playlyna"
        val md = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, t)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurationMs)
            .build()
        mediaSession.setMetadata(md)

        val st = if (currentIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val ps = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(st, currentPositionMs, 1f)
            .build()
        mediaSession.setPlaybackState(ps)
        updateNotification()
    }

    override fun onDestroy() {
        try { mediaSession.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "playlyna_playback"
        const val NOTIF_ID = 4242
    }
}
