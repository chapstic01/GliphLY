package dev.gliphly

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager

/** Channel indices for Phone (3a)/(3a) Pro, per the GDK README. */
object Zones {
    val A = (20..30).toList()   // A1 (top) .. A11
    val B = (31..35).toList()   // B1 .. B5
    val C = (0..19).toList()    // C1 .. C20
    val ALL = A + B + C
}

/** Owns the GlyphManager lifecycle: init -> register -> openSession -> frames -> close. */
class GlyphController(context: Context, private val onStatus: (String) -> Unit) {
    private val gm = GlyphManager.getInstance(context.applicationContext)
    private var ready = false

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            if (!Common.is24111()) { onStatus("Unsupported: needs Phone (3a) / (3a) Pro"); return }
            runCatching {
                gm.register(Glyph.DEVICE_24111)
                gm.openSession()
                ready = true
                onStatus("Connected")
            }.onFailure { onStatus("Session failed: ${it.message}") }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            ready = false
            runCatching { gm.closeSession() }
            onStatus("Disconnected")
        }
    }

    fun start() = gm.init(callback)

    fun stop() {
        runCatching { gm.turnOff(); gm.closeSession() }
        gm.unInit()
        ready = false
    }

    /** Show exactly these channels (toggling a new frame replaces the previous one). */
    fun show(channels: Set<Int>) {
        if (!ready) return
        runCatching {
            if (channels.isEmpty()) gm.turnOff()
            else {
                val b = gm.glyphFrameBuilder
                channels.forEach { b.buildChannel(it) }
                gm.toggle(b.build())
            }
        }.onFailure { Log.e("GlyphLab", "show failed", it) }
    }

    fun off() { if (ready) runCatching { gm.turnOff() } }
}
