package app.gaborbiro.sparescrape

import java.io.File
import java.util.*

const val ROOT_URL = "https://www.spareroom.co.uk"
const val ROOT_URL_MOBILE = "https://m.spareroom.co.uk"

const val WHOLE_FOODS_COORDS = "51.5105546,-0.1383121"

val GOOGLE_MAPS_API_KEY: String by lazy {
    val properties = Properties()
    properties.load(File("local.properties").inputStream())
    properties["GOOGLE_MAPS_API_KEY"] as String
}

const val AVG_WEEKS_IN_MONTH = 365.0 / 7 / 12

const val MISSING_VALUE = "missing"