package com.example.moviesta

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var exo: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaUrl = intent.getStringExtra("url")?.trim()
        if (mediaUrl.isNullOrEmpty() || !(mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))) {
            Toast.makeText(this, "Invalid video URL", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(mediaUrl)))
            prepare()
            playWhenReady = true
        }
        exo = player
        setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = true
                        keepScreenOn = true
                    }
                }
            )
        }
    }

    override fun onStop() {
        exo?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        exo?.release()
        exo = null
        super.onDestroy()
    }
}
