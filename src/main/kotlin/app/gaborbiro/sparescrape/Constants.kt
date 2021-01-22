package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.model.LonLat
import java.io.File
import java.util.*

const val ROOT_URL = "https://www.spareroom.co.uk"
const val ROOT_URL_2 = "www.spareroom.co.uk"
const val ROOT_URL_3 = "http://www.spareroom.co.uk"
const val ROOT_URL_MOBILE = "https://m.spareroom.co.uk"
const val ROOT_URL_MOBILE_2 = "m.spareroom.co.uk"
const val ROOT_URL_MOBILE_3 = "http://m.spareroom.co.uk"


val WHOLE_FOODS_COORDS = LonLat("51.5105546", "-0.1383121")
val TAMAS_COORDS = LonLat("51.5156236","-0.0679687")
val SCHWANN_COORDS = LonLat("51.4999826","-0.0501279")

val destinations = mapOf(
    "Whole Foods" to Pair(WHOLE_FOODS_COORDS, .5f),
    "Tamas" to Pair(TAMAS_COORDS, 2f),
    "Schwann" to Pair(SCHWANN_COORDS, 3f)
)

val GOOGLE_MAPS_API_KEY: String by lazy {
    val properties = Properties()
    properties.load(File("local.properties").inputStream())
    properties["GOOGLE_MAPS_API_KEY"] as String
}

const val AVG_WEEKS_IN_MONTH = 365.0 / 7 / 12

const val MISSING_VALUE = "missing"

const val MAX_PRICE = 800
const val MAX_DISTANCE_MIN = 35
const val MAX_FLATMATES = 3
const val MAX_BEDROOMS = 4