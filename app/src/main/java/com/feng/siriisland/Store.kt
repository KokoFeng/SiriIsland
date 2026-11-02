package com.feng.siriisland

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private lateinit var p: SharedPreferences
    fun init(c: Context) {
        if (!::p.isInitialized) p = c.applicationContext.getSharedPreferences("siri", Context.MODE_PRIVATE)
    }
    val prefs: SharedPreferences get() = p

    var apiKey: String
        get() = p.getString("key", "") ?: ""
        set(v) { p.edit().putString("key", v).apply() }

    private fun all(): JSONArray = try { JSONArray(p.getString("convs", "[]")) } catch (e: Exception) { JSONArray() }
    private fun save(a: JSONArray) { p.edit().putString("convs", a.toString()).apply() }

    fun newConv(): String {
        val id = System.currentTimeMillis().toString()
        val a = all()
        a.put(JSONObject().put("id", id).put("title", "New chat")
            .put("time", System.currentTimeMillis()).put("msgs", JSONArray()))
        save(a)
        return id
    }

    fun add(id: String, role: String, text: String) {
        val a = all()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("id") == id) {
                val m = o.getJSONArray("msgs")
                m.put(JSONObject().put("r", role).put("t", text))
                if (role == "user" && m.length() == 1) o.put("title", text.take(40))
                o.put("time", System.currentTimeMillis())
            }
        }
        save(a)
    }

    fun get(id: String): JSONObject? {
        val a = all()
        for (i in 0 until a.length()) {
            if (a.getJSONObject(i).getString("id") == id) return a.getJSONObject(i)
        }
        return null
    }

    fun list(): List<JSONObject> {
        val a = all()
        return (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.getJSONArray("msgs").length() > 0 }
            .sortedByDescending { it.getLong("time") }
    }
}
