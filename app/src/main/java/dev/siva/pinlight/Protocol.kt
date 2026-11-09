package dev.siva.pinlight

// Shared between the app process and the hooked System UI process.
object Protocol {
    const val SELF = "dev.siva.pinlight"
    const val SYSTEMUI = "com.android.systemui"

    const val ACTION_UPDATE = "dev.siva.pinlight.UPDATE"   // app -> System UI
    const val ACTION_REQUEST = "dev.siva.pinlight.REQUEST" // System UI / app UI -> listener
    const val ACTION_PING = "dev.siva.pinlight.PING"       // app UI -> System UI
    const val ACTION_PONG = "dev.siva.pinlight.PONG"       // System UI -> app UI
    const val PERMISSION = "dev.siva.pinlight.permission.UPDATE"
    const val EXTRA_STATUS = "status"
    const val LOG_TAG = "PinLight"

    const val EXTRA_PACKAGES = "packages"
    const val EXTRA_COLORS = "colors"
    const val EXTRA_ICONS = "icons"
    const val EXTRA_PREVIEW = "preview"

    const val MAX_PINS = 4
    const val ICON_PX = 72
}
