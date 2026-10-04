package com.tuckercr.hark

/** Which sound plays when the wake word is heard. */
sealed interface AlertSound {
    /** The system default alarm tone. */
    data object Default : AlertSound

    /** No sound at all. */
    data object Silent : AlertSound

    data class Custom(
        val uri: String,
    ) : AlertSound

    companion object {
        /**
         * Maps the URI returned by the system ringtone picker. A null URI means the user chose
         * "Silent"; the default alarm URI means they chose "Default".
         */
        fun fromPickedUri(
            picked: String?,
            defaultUri: String?,
        ): AlertSound =
            when {
                picked == null -> Silent
                picked == defaultUri -> Default
                else -> Custom(picked)
            }
    }
}

/** How long the alert sound plays. Short sounds are looped to fill timed durations. */
enum class AlertDuration(
    val limitMillis: Long?,
    val loops: Boolean,
) {
    ONCE(limitMillis = null, loops = false),
    SECONDS_5(limitMillis = 5_000L, loops = true),
    SECONDS_10(limitMillis = 10_000L, loops = true),
    SECONDS_30(limitMillis = 30_000L, loops = true),
    UNTIL_DISMISSED(limitMillis = null, loops = true),
    ;

    companion object {
        val DEFAULT = SECONDS_5

        fun fromName(name: String?): AlertDuration = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

data class AlertSettings(
    val sound: AlertSound = AlertSound.Default,
    val duration: AlertDuration = AlertDuration.DEFAULT,
)
