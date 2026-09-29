package dev.gliphly

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

/**
 * Turns whatever's playing on the phone into three "VU meter" style displays:
 * strip C fills up with loudness, strip A lights top-down on stronger peaks,
 * and B flashes on the loudest hits. Capture uses Android's Playback Capture
 * API, which requires a one-time screen-capture-style consent prompt (that's
 * how Android gates access to other apps' audio) and runs via a foreground
 * service while active. Foreground-app-only for the Glyph output itself,
 * same as everything else.
 */
@Composable
fun VisualizerScreen(glyph: GlyphController) {
    val ctx = LocalContext.current
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val level by AudioBus.level.collectAsState()
    val running by AudioBus.running.collectAsState()
    val scope = rememberCoroutineScope()

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svcIntent = Intent(ctx, PlaybackCaptureService::class.java)
                .putExtra("resultCode", result.resultCode)
                .putExtra("data", result.data)
            ctx.startForegroundService(svcIntent)
        } else {
            errorMsg = "Capture permission denied"
        }
    }

    // Drive the Glyph from AudioBus.level while capture is running.
    DisposableEffect(running) {
        var job: Job? = null
        if (running) {
            job = scope.launch {
                while (isActive) {
                    val l = level
                    val cCount = (l * Zones.C.size).toInt().coerceIn(0, Zones.C.size)
                    val aCount = if (l > 0.55f) ((l - 0.55f) / 0.45f * Zones.A.size).toInt().coerceIn(0, Zones.A.size) else 0
                    val zones = Zones.C.take(cCount).toMutableSet()
                    zones += Zones.A.take(aCount)
                    if (l > 0.85f) zones += Zones.B
                    glyph.show(zones)
                    delay(60)
                }
            }
        }
        onDispose { job?.cancel(); if (running) glyph.off() }
    }

    Column(Modifier.fillMaxSize().background(NBlack).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Label("Music visualizer")
        Text(
            "Mirrors whatever's playing system-wide onto the Glyph. Start music, tap start, and allow the capture prompt.",
            color = NGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton(if (running) "Stop" else "Start", selected = running) {
                errorMsg = null
                if (running) {
                    ctx.stopService(Intent(ctx, PlaybackCaptureService::class.java))
                } else {
                    val mgr = ctx.getSystemService(MediaProjectionManager::class.java)
                    projectionLauncher.launch(mgr.createScreenCaptureIntent())
                }
            }
            if (running) Text("LEVEL ${(level * 100).toInt()}%", color = NWhite, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        errorMsg?.let { Text(it.uppercase(), color = NGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }

        Box(Modifier.fillMaxWidth().height(16.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(NCard)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(NRed))
        }
    }
}
