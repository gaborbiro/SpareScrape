package app.gaborbiro.sparescrape.data

import java.util.prefs.Preferences

object Preferences {

    fun save(key: String, value: String) {
        val prefs = Preferences.userRoot()
        if (value.length > Preferences.MAX_VALUE_LENGTH) {
            val tokenCount: Int = value.length / Preferences.MAX_VALUE_LENGTH
            val tokens = (0..tokenCount).map {
                val min: Int = kotlin.math.min((it + 1) * Preferences.MAX_VALUE_LENGTH, value.length)
                value.substring(it * Preferences.MAX_VALUE_LENGTH, min)
            }
            tokens.forEachIndexed { i, s ->
                prefs.put(getTokenKey(key, i), s)
            }
            prefs.putInt(getSequenceKey(key), tokenCount)
        } else {
            prefs.put(key, value)
        }
    }

    fun get(key: String, default: String?): String? {
        val prefs = Preferences.userRoot()
        prefs[getSequenceKey(key), null]?.let {
            val tokenCount = it.toInt()
            return (0..tokenCount).map {
                prefs[getTokenKey(key, it), null]!!
            }.joinToString("")
        } ?: run {
            return prefs[key, default]
        }
    }

    fun clear(key: String) {
        val prefs = Preferences.userRoot()
        prefs[getSequenceKey(key), null]?.let {
            val tokenCount = it.toInt()
            (0..tokenCount).forEach {
                prefs.remove(getTokenKey(key, it))
            }
            prefs.remove(getSequenceKey(key))
        } ?: run {
            prefs.remove(key)
        }
    }

    private fun getSequenceKey(key: String) = key + "_sequence"
    private fun getTokenKey(key: String, index: Int) = key + "_" + index
}