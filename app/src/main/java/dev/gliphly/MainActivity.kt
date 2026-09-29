package dev.gliphly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

// Nothing's own visual language, extended with the red accent their "Glyph
// Studio" Live feature uses for active states — dark cards, rounded corners,
// uppercase monospace labels.
val NBlack = Color.Black
val NWhite = Color(0xFFFAFAFA)
val NGray = Color(0xFF8A8A8A)
val NHairline = Color(0xFF2A2A2A)
val NCard = Color(0xFF161616)
val NRed = Color(0xFFFF3B30)
val NRedDim = Color(0x33FF3B30)
val NMono = FontFamily.Monospace

class MainActivity : ComponentActivity() {
    private lateinit var glyph: GlyphController
    private var status by mutableStateOf("CONNECTING")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        glyph = GlyphController(this) { status = it.uppercase() }
        glyph.start()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = NBlack, surface = NBlack)) {
                AppRoot(glyph, status)
            }
        }
    }

    override fun onDestroy() { glyph.stop(); super.onDestroy() }
}

private enum class Screen { EDITOR, VISUALIZER }

@Composable
fun AppRoot(glyph: GlyphController, status: String) {
    var screen by remember { mutableStateOf(Screen.EDITOR) }
    Column(Modifier.fillMaxSize().background(NBlack).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TabLabel("Editor", screen == Screen.EDITOR) { screen = Screen.EDITOR }
            TabLabel("Visualizer", screen == Screen.VISUALIZER) { screen = Screen.VISUALIZER }
        }
        Hairline()
        when (screen) {
            Screen.EDITOR -> Editor(glyph, status)
            Screen.VISUALIZER -> VisualizerScreen(glyph)
        }
    }
}

@Composable
fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        color = if (selected) NRed else NGray,
        fontFamily = NMono,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun Label(text: String) {
    Text(text.uppercase(), color = NGray, fontFamily = NMono, fontSize = 11.sp, letterSpacing = 1.sp)
}

@Composable
fun Hairline() { Box(Modifier.fillMaxWidth().height(1.dp).background(NHairline)) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DotStrip(label: String, zones: List<Int>, on: Set<Int>, toggle: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NCard).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Label(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            zones.forEach { z ->
                val isOn = z in on
                Box(
                    Modifier.size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isOn) NRed else NHairline)
                        .clickable { toggle(z) }
                )
            }
        }
    }
}

@Composable
fun NButton(text: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) NRedDim else NCard
    val border = if (selected) NRed else if (enabled) NHairline else NCard
    val textColor = if (selected) NRed else if (enabled) NWhite else NGray
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text.uppercase(), color = textColor, fontFamily = NMono, fontSize = 12.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun UpdateRow() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NButton("Check for updates") {
            msg = "Checking"
            scope.launch(Dispatchers.IO) {
                val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.1"
                val update = UpdateChecker.checkLatest(current)
                withContext(Dispatchers.Main) {
                    if (update == null) msg = "Up to date"
                    else { msg = "Downloading ${update.tag}"; UpdateChecker.downloadAndPromptInstall(ctx, update) }
                }
            }
        }
        msg?.let { Text(it.uppercase(), color = NGray, fontFamily = NMono, fontSize = 11.sp) }
    }
}

@Composable
fun Editor(glyph: GlyphController, status: String) {
    var on by remember { mutableStateOf(setOf<Int>()) }
    val steps = remember { mutableStateListOf<Set<Int>>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    fun apply(s: Set<Int>) { on = s; glyph.show(s) }
    fun stopPlay() { job?.cancel(); job = null; glyph.off() }
    val flip = { z: Int -> apply(if (z in on) on - z else on + z) }

    Column(
        Modifier.fillMaxSize().background(NBlack).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("GLIPHLY", color = NWhite, fontFamily = NMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (status == "CONNECTED") NRed else NGray))
            Text(status, color = NGray, fontFamily = NMono, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Hairline()
        UpdateRow()
        Hairline()

        DotStrip("A · Top strip", Zones.A, on, flip)
        DotStrip("B", Zones.B, on, flip)
        DotStrip("C · Main strip", Zones.C, on, flip)

        Hairline()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton("All") { apply(Zones.ALL.toSet()) }
            NButton("Clear") { apply(emptySet()) }
            NButton("Add step") { steps.add(on) }
        }
        Label("${steps.size} steps recorded")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NButton("Play loop", enabled = job == null && steps.isNotEmpty()) {
                job = scope.launch { while (isActive) for (s in steps.toList()) { apply(s); delay(400) } }
            }
            NButton("Stop", enabled = job != null) { stopPlay() }
            NButton("Reset") { stopPlay(); steps.clear() }
        }
    }
}
