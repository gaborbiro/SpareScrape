package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.model.Property
import app.gaborbiro.sparescrape.data.model.PropertyWithDistance
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Collectors


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

fun perWeekToPerMonth(price: String): Pair<String, Int> {
    if (price == MISSING_VALUE) {
        return Pair(MISSING_VALUE, -1)
    }
    val pricePerMonth = if (price.endsWith("pw")) {
        val matcher = price.matcher("([^\\d])([\\d\\.,]+)[\\s]*pw")
        if (matcher.find()) {
            val perMonth: Double = matcher.group(2).toFloat() * AVG_WEEKS_IN_MONTH
            "${matcher.group(1)}${perMonth.toInt()} pcm"
        } else {
            price
        }
    } else price
    val matcher = pricePerMonth.matcher("([^\\d])([\\d\\.,]+)[\\s]*pcm")
    return if (matcher.find()) {
        Pair(pricePerMonth, matcher.group(2).replace(",", "").toInt())
    } else {
        println("Error parsing price $price")
        Pair(pricePerMonth, -1)
    }
}

inline fun <reified T> T.prettyPrint(): String {
    val fields = mutableListOf<Field>().apply {
        getAllFields(this, T::class.java)
    }
    val map = fields.map {
        it.isAccessible = true
        it.name to (it[this]?.toString() ?: "")
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

fun isValidUrl(url: String) = url.startsWith(ROOT_URL)

fun cleanUrl(url: String): String {
    var cleanUrl = url.trim()
    if (cleanUrl.startsWith(ROOT_URL_2)) { // www.spareroom.co.uk
        cleanUrl = cleanUrl.replace(ROOT_URL_2, ROOT_URL)
    }
    if (cleanUrl.startsWith(ROOT_URL_3)) { // http://www.spareroom.co.uk
        cleanUrl = cleanUrl.replace(ROOT_URL_3, ROOT_URL)
    }
    if (cleanUrl.startsWith(ROOT_URL_MOBILE_2)) { // m.spareroom.co.uk
        cleanUrl = cleanUrl.replace(ROOT_URL_MOBILE_2, ROOT_URL)
    }
    if (cleanUrl.startsWith(ROOT_URL_MOBILE_3)) { // http://m.spareroom.co.uk
        cleanUrl = cleanUrl.replace(ROOT_URL_MOBILE_3, ROOT_URL)
    }
    if (cleanUrl.startsWith(ROOT_URL_MOBILE)) { // https://m.spareroom.co.uk
        cleanUrl = cleanUrl.replace(ROOT_URL_MOBILE, ROOT_URL)
    }
    return cleanUrl
}

fun validate(property: Property): Boolean {
    if (property is PropertyWithDistance) {
        if (property.distances.any { it.routes.minByOrNull { it.timeMinutes }!!.timeMinutes > 40 }) {
            return false
        }
    }
    if (property.pricePerMonthInt > 850) return false
    if (property.furnishings != MISSING_VALUE && property.furnishings.equals(
            "Unfurnished",
            ignoreCase = true
        )
    ) return false
    if (property.livingRoom != MISSING_VALUE && property.livingRoom.equals("No", ignoreCase = true)) return false
    if (property.flatmates != MISSING_VALUE && property.flatmates.toInt() > 3) return false
    if (property.totalRooms != MISSING_VALUE && property.totalRooms.toInt() > 4) return false
    return true
}

