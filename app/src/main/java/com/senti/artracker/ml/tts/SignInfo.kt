package com.senti.artracker.ml.tts

data class SignInfo(
    val signName: String,
    val label: String,
    val description: String
) {
    fun toTTSText(): String =
        "$signName. $description"
}
