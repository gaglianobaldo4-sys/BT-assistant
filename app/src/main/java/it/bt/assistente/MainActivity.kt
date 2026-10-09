
package it.bt.assistente

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.text.InputType
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var apiKey: EditText
    private lateinit var prompt: EditText
    private lateinit var chat: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var send: Button

    private val history = JSONArray()

    private val greeting =
        "Buongiorno, capo. È tutto operativo, " +
        "i neuroni sono connessi. " +
        "Puoi chiedermi tutto quel che vuoi."

    private val instructions = """
        Ti chiami BT, come BT-7274 di Titanfall 2.
        Parli italiano, sei leale, calmo, intelligente
        e leggermente ironico. Chiami l'utente capo.
        Aiuti con le attività quotidiane.
        Non affermare di aver eseguito operazioni sul
        telefono se non hai uno strumento che le esegue.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF101722.toInt())
        }

        val title = TextView(this).apply {
            text = "BT // SISTEMI ONLINE"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(0xFF70C8FF.toInt())
        }

        val keyLabel = TextView(this).apply {
            text = "Chiave Groq (non condividerla)"
            setTextColor(0xFFFFFFFF.toInt())
        }

        apiKey = EditText(this).apply {
            hint = "Incolla la tua API key"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
        }

        chat = TextView(this).apply {
            text = "BT: $greeting\n\n"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(4, 12, 4, 12)
        }

        val scroll = ScrollView(this).apply {
            addView(chat)
        }

        prompt = EditText(this).apply {
            hint = "Dimmi, capo..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
        }

        send = Button(this).apply {
            text = "Invia"
            setOnClickListener { sendMessage() }
        }

        val mic = Button(this).apply {
            text = "🎙 Parla"
            setOnClickListener { listen() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(send, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            addView(mic, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
        }

        root.addView(title)
        root.addView(keyLabel)
        root.addView(apiKey)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(prompt)
        root.addView(buttons)

        setContentView(root)

        history.put(JSONObject()
            .put("role", "system")
            .put("content", instructions))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.ITALIAN
        }
    }

    private fun speak(text: String) {
        runOnUiThread {
            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "BT_REPLY"
            )
        }
    }

    private fun listen() {
        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "it-IT"
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Dimmi, capo."
            )
        }

        try {
            startActivityForResult(intent, 101)
        } catch (e: Exception) {
            append("BT: Riconoscimento vocale non disponibile.")
        }
    }

    @Deprecated("Compatibilità con il prototipo")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()

            if (!words.isNullOrBlank()) {
                prompt.setText(words)
                sendMessage()
            }
        }
    }

    private fun append(text: String) {
        runOnUiThread {
            chat.append("$text\n\n")
        }
    }

    private fun sendMessage() {
        val key = apiKey.text.toString().trim()
        val question = prompt.text.toString().trim()

        if (key.isEmpty()) {
            append("BT: Inserisci la tua chiave Groq, capo.")
            return
        }

        if (question.isEmpty()) return

        prompt.setText("")
        append("TU: $question")
        send.isEnabled = false

        Thread {
            var connection: HttpURLConnection? = null

            try {
                history.put(JSONObject()
                    .put("role", "user")
                    .put("content", question))

                val payload = JSONObject()
                    .put("model", "openai/gpt-oss-20b")
                    .put("messages", history)
                    .put("temperature", 0.7)

                connection = (URL(
                    "https://api.groq.com/openai/v1/chat/completions"
                ).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty(
                        "Authorization", "Bearer $key"
                    )
                    setRequestProperty(
                        "Content-Type", "application/json"
                    )
                }

                OutputStreamWriter(
                    connection.outputStream, Charsets.UTF_8
                ).use { it.write(payload.toString()) }

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val raw = stream?.bufferedReader()
                    ?.use { it.readText() } ?: ""

                if (code !in 200..299) {
                    throw Exception("HTTP $code: $raw")
                }

                val answer = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                history.put(JSONObject()
                    .put("role", "assistant")
                    .put("content", answer))

                append("BT: $answer")
                speak(answer)

            } catch (e: Exception) {
                append(
                    "BT: Connessione non riuscita. " +
                    "Controlla Internet, API key e modello. " +
                    (e.message ?: "")
                )
            } finally {
                connection?.disconnect()
                runOnUiThread { send.isEnabled = true }
            }
        }.start()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
