package dev.gliphly

import kotlinx.coroutines.flow.MutableStateFlow

/** Simple in-process channel: the capture service writes levels here, the UI reads them. */
object AudioBus {
    val level = MutableStateFlow(0f)
    val running = MutableStateFlow(false)
}
