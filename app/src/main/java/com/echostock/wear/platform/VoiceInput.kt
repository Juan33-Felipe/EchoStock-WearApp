package com.echostock.wear.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable

/**
 * Voz a texto mediante el reconocedor del sistema (RF-001). Pedimos varias hipótesis
 * para que la búsqueda pueda elegir la que mejor encaja con el catálogo cuando el ruido
 * del almacén deforma la primera (RNF-004). El micrófono solo se abre mientras el
 * operario dicta (RNF-005).
 */
class RecognizeSpeech : ActivityResultContract<String, List<String>?>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String>? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.filter { it.isNotBlank() }
    }

    companion object {
        fun isAvailable(context: Context): Boolean =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }
}

/** Devuelve una función que abre el dictado; `onResult(null)` si se canceló o no se entendió. */
@Composable
fun rememberVoiceInput(onResult: (List<String>?) -> Unit): (prompt: String) -> Unit {
    val launcher = rememberLauncherForActivityResult(RecognizeSpeech(), onResult)
    return { prompt ->
        try {
            launcher.launch(prompt)
        } catch (e: android.content.ActivityNotFoundException) {
            onResult(null)
        }
    }
}
