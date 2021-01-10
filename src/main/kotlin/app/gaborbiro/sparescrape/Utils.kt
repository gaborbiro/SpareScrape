package app.gaborbiro.sparescrape

import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.Arrays





@Throws(Exception::class)
fun callGet(url: String): String {
    val conn: HttpURLConnection = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    return BufferedReader(InputStreamReader(conn.inputStream)).use {
        it.lines().collect(Collectors.joining())
    }
}

fun String.matcher(regex: String): Matcher = Pattern.compile(regex).matcher(this)

fun addPadding(text: String, totalLength: Int): String {
    return if (text.length < totalLength) {
        text + String(CharArray(totalLength - text.length) { ' ' })
    } else {
        text
    }
}

fun perWeekToPerMonth(price: String): String {
    return if (price.endsWith("pw")) {
        val matcher = price.matcher("([^\\d])([\\d\\.,]+)[\\s]*pw")
        if (matcher.find()) {
            val perMonth: Double = matcher.group(2).toFloat() * AVG_WEEKS_IN_MONTH
            "${matcher.group(1)}${perMonth.toInt()} pcm"
        } else {
            price
        }
    } else price
}

inline fun <reified T> T.prettyPrint(): String {
    val fields = mutableListOf<Field>().apply {
        getAllFields(this, T::class.java)
    }
    val map = fields.map {
        it.isAccessible = true
        it.name to it[this].toString()
    }.associate { it }
    val longestKeyLength = map.keys.maxByOrNull { it.length }!!.length
    return map.map {
        addPadding(it.key, longestKeyLength + 1) + ": " + it.value
    }.joinToString("\n")
}

fun getAllFields(fields: MutableList<Field>, type: Class<*>): List<Field> {
    fields.addAll(listOf(*type.declaredFields))
    if (type.superclass != null) {
        getAllFields(fields, type.superclass)
    }
    return fields
}
