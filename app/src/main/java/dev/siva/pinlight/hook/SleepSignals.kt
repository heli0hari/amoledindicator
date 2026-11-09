package dev.siva.pinlight.hook

import android.os.Handler
import android.os.Looper

// Bridge from the sleep/wake hooks to the overlay, always on the main thread.
object SleepSignals {
    private val handler = Handler(Looper.getMainLooper())
    var listener: ((asleep: Boolean) -> Unit)? = null

    fun post(asleep: Boolean) {
        handler.post { listener?.invoke(asleep) }
    }
}
