package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.model.Price
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

fun perWeekToPerMonth(prices: Array<String>): Array<Price> {
    return prices.map { perWeekToPerMonth(it) }.toTypedArray()
}

fun perWeekToPerMonth(price: String): Price {
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
        Price(price, pricePerMonth, matcher.group(2).replace(",", "").toInt())
    } else {
        println("Error parsing price $price")
        Price(price, pricePerMonth, -1)
    }
}

fun Map<*, *>.prettyPrint(): String {
    val longestKeyLength = keys.maxByOrNull { it.toString().length }!!.toString().length
    return map {
        addPadding(it.key.toString(), longestKeyLength + 1) + ": " + it.value
    }.joinToString("\n")
}

inline fun <reified T> T.prettyPrint(): String {
    val fields = mutableListOf<Field>().apply {
        getAllFields(this, T::class.java)
    }
    val map: Map<String, String> = fields.map {
        it.isAccessible = true
        val strValue = when (val value = it[this]) {
            is Iterable<*> -> value.joinToString(", ")
            is Array<*> -> value.joinToString(", ")
            else -> value?.toString() ?: ""
        }
        it.name to (strValue)
    }.associate { it }
    return map.prettyPrint()
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
        if (property.distances.any { it.routes.minByOrNull { it.timeMinutes }!!.timeMinutes > MAX_DISTANCE_MIN }) {
            println("Rejected: Too far")
            return false
        }
    }
    if (property.prices.all { it.pricePerMonthInt > MAX_PRICE }) {
        println("Rejected: Too exp")
        return false
    }
    if (property.furnishings != MISSING_VALUE && property.furnishings.equals(
            "Unfurnished",
            ignoreCase = true
        )
    ) {
        println("Rejected: Unfurnished")
        return false
    }
    if (property.livingRoom != MISSING_VALUE && property.livingRoom.equals("No", ignoreCase = true)) {
        println("Rejected: No living room")
        return false
    }
    if (property.flatmates != MISSING_VALUE && property.flatmates.toInt() > MAX_FLATMATES) {
        println("Rejected: More than 3 flatmates")
        return false
    }
    if (property.totalRooms != MISSING_VALUE && property.totalRooms.toInt() > MAX_BEDROOMS) {
        println("Rejected: More than 4 bedrooms")
        return false
    }
    return true
}