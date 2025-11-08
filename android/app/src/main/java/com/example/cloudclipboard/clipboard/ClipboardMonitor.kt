package com.example.cloudclipboard.clipboard

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ClipboardMonitor(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val clipboardManager =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _events = MutableSharedFlow<ClipboardEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ClipboardEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(dispatcher)

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
        val item = clip.getItemAt(0)
        val text = item.text?.toString().orEmpty()
        if (text.isBlank()) return@OnPrimaryClipChangedListener

        scope.launch {
            Timber.d("Clipboard changed")
            _events.emit(ClipboardEvent.Text(text))
        }
    }

    fun start() {
        clipboardManager.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboardManager.removePrimaryClipChangedListener(listener)
    }

    sealed interface ClipboardEvent {
        data class Text(val text: String) : ClipboardEvent
    }
}
