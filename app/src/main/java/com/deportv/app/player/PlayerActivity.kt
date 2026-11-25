package com.deportv.app.player

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.deportv.app.data.XtreamClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Reproductor propio. Arriba/abajo cambia entre los canales que emiten el evento;
 * si un canal falla, salta solo al siguiente. Aquí irá el panel de LaLiga Fantasy (fase 3).
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_URLS = "urls"
        const val EXTRA_NAMES = "names"
    }

    private lateinit var player: ExoPlayer
    private lateinit var osd: TextView
    private lateinit var urls: Array<String>
    private lateinit var names: Array<String>
    private var title = ""
    private var index = 0
    private var failures = 0
    private val handler = Handler(Looper.getMainLooper())
    private val hideOsd = Runnable { osd.animate().alpha(0f).setDuration(400).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        urls = intent.getStringArrayExtra(EXTRA_URLS) ?: run { finish(); return }
        names = intent.getStringArrayExtra(EXTRA_NAMES) ?: Array(urls.size) { "Canal ${it + 1}" }
        title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true).build()
        val dsf = OkHttpDataSource.Factory(http).setUserAgent(XtreamClient.UA)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000).build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsf))
            .setLoadControl(loadControl)
            .build()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val view = PlayerView(this).apply {
            this.player = this@PlayerActivity.player
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            keepScreenOn = true
        }
        osd = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 20f
            setBackgroundColor(0xB0000000.toInt()); setPadding(40, 24, 40, 24)
        }
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(osd, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(48, 36, 0, 0) })
        setContentView(root)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failures++
                if (failures < urls.size * 2) {
                    // Primero reintenta en HLS, luego salta al siguiente canal
                    val cur = urls[index]
                    if (cur.endsWith(".ts")) {
                        urls[index] = cur.removeSuffix(".ts") + ".m3u8"
                    } else {
                        Toast.makeText(this@PlayerActivity, "${names[index]} no responde, probando otro canal…", Toast.LENGTH_SHORT).show()
                        index = (index + 1) % urls.size
                    }
                    load()
                } else {
                    Toast.makeText(this@PlayerActivity, "No se pudo reproducir: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) failures = 0
            }
        })
        load()
    }

    private fun load() {
        val url = urls[index]
        val item = MediaItem.Builder().setUri(url).apply {
            if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        showOsd()
    }

    private fun showOsd() {
        val extra = if (urls.size > 1) "\n${names[index]}   (▲▼ cambiar canal ${index + 1}/${urls.size})" else "\n${names[index]}"
        osd.text = title + extra
        osd.alpha = 1f
        handler.removeCallbacks(hideOsd)
        handler.postDelayed(hideOsd, 5000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> if (urls.size > 1) {
                index = (index + urls.size - 1) % urls.size; failures = 0; load(); return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (urls.size > 1) {
                index = (index + 1) % urls.size; failures = 0; load(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> { showOsd(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) player.play()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
