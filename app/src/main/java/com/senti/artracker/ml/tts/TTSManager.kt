package com.senti.artracker.ml.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val polishLocale = Locale("pl", "PL")
            val result = tts.setLanguage(polishLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Polish language not supported, falling back to default")
                tts.setLanguage(Locale.getDefault())
            }
            isInitialized = true
            Log.i(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    fun speak(label: String) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, skipping: $label")
            return
        }
        val ttsText = TTSTranslationService.translate(label)
        tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, label)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        isInitialized = false
    }
}
