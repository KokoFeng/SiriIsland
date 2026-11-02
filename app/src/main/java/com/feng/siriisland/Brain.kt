package com.feng.siriisland

import android.os.Bundle
import android.speech.RecognitionListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

open class Rl : RecognitionListener {
    override fun onReadyForSpeech(p: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(r: Float) {}
    override fun onBufferReceived(b: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(e: Int) {}
    override fun onResults(b: Bundle?) {}
    override fun onPartialResults(b: Bundle?) {}
    override fun onEvent(t: Int, p: Bundle?) {}
}

object Brain {
    fun ask(key: String, conv: String): String {
        val msgs = Store.get(conv)?.getJSONArray("msgs") ?: JSONArray()
        val h = JSONArray()
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            h.put(JSONObject().put("role", if (m.getString("r") == "user") "user" else "assistant")
                .put("content", m.getString("t")))
        }
        val body = JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 600)
            .put("system", "You are Siri, a friendly voice assistant. Answer briefly and warmly, " +
                "without markdown, in the same language the user uses.")
            .put("messages", h).toString()
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) throw Exception("HTTP $code. Check your API key.")
        val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("content")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "text" }.joinToString("") { it.getString("text") }
    }
}
