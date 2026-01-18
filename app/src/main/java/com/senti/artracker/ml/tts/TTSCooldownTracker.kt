package com.senti.artracker.ml.tts

class TTSCooldownTracker(private val cooldownMillis: Long = DEFAULT_COOLDOWN_MS) {

    companion object {
        const val DEFAULT_COOLDOWN_MS = 5_000L
    }

    private val lastSpokenTimestamps = mutableMapOf<String, Long>()

    fun canSpeak(label: String): Boolean {
        val lastSpoken = lastSpokenTimestamps[label] ?: return true
        return System.currentTimeMillis() - lastSpoken >= cooldownMillis
    }

    fun markSpoken(label: String) {
        lastSpokenTimestamps[label] = System.currentTimeMillis()
    }
}
