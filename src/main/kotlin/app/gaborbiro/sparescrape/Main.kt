package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.Preferences
import app.gaborbiro.sparescrape.data.PropertyRepository
import app.gaborbiro.sparescrape.data.model.*
import com.google.gson.Gson
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private val gson = Gson()

private lateinit var driver: WebDriver

fun main(args: Array<String>) {

    BufferedReader(InputStreamReader(System.`in`)).use { reader ->
        val commands = setupCommandLineCommands(reader)
        var command: String?
        do {
            println()
            printCommands(commands)
            println()
            command = reader.readLine()
            if (command == EXIT_COMMAND_CODE) {
                return@use
            }

            commands[command]
                ?.exec?.apply {
                    runCatching { invoke() }.exceptionOrNull()?.printStackTrace()
                }
                ?: run { println("Invalid command '$command'") }
        } while (command != EXIT_COMMAND_CODE)
    }

    cleanupBrowser()
}

private fun setupCommandLineCommands(reader: BufferedReader): Map<String, Command> {
    return mutableMapOf(
        "1" to Command("scrape inbox for property links (cached)") {
            scrapeMessagesForLinks()
        },
        "2" to Command("scrape inbox links for property data (cached)") {
            scrapeLinksForProperties()
        },
        "3" to Command("calculate distances") {
            calculateDistances()
        },
        "4" to Command("print property links from messages") {
            println(PropertyRepository.getJsonLinks())
        },
        "5" to Command("print properties") {
            println(PropertyRepository
                .getProperties()
                .joinToString("\n\n") { it.prettyPrint() })
        },
        "6" to Command("print properties with distances calculated") {
            println(PropertyRepository
                .getPropertiesWithDistance()
                .joinToString("\n\n") { it.prettyPrint() })
        },
        "7" to Command("delete message links") {
            PropertyRepository.clearLinks()
        },
        "8" to Command("delete properties") {
            PropertyRepository.clearProperties()
        },
        "9" to Command("delete properties with distances") {
            PropertyRepository.clearPropertiesWithDistance()
        },
        "10" to Command("delete session cookies (will re-login)") {
            Preferences.clear(PREF_COOKIES)
        },
        "11" to Command("add property") {
            print("property url: ")
            val url = reader.readLine()
            addPropertyToPreferences(url.trim())
        },
        "12" to Command("delete property") {
            print("property url: ")
            val url = reader.readLine()
            deleteProperty(url.trim())
        }
    ).let {
        if (it.containsKey(EXIT_COMMAND_CODE)) {
            throw IllegalStateException("Command code $EXIT_COMMAND_CODE is reserved!")
        }
        it[EXIT_COMMAND_CODE] = Command("quit") {}
        it.toMap()
    }
}

private fun scrapeMessagesForLinks() {
    ensureBrowser()
    val links = Scrape.scrapeLinksFromMessages(driver)
    PropertyRepository.saveLinks(links)
}

private fun scrapeLinksForProperties() {
    ensureBrowser()
    val messageLinks: List<MessageLinkSet> = PropertyRepository.getLinks()
    if (messageLinks.isEmpty()) {
        println("Link cache empty. Fetch links first")
        return
    }
    val properties: List<Property> = messageLinks.map { linkGroup ->
        linkGroup.links.mapNotNull { link ->
            Scrape.scrapeProperty(driver, link, linkGroup.senderName)
        }
    }.flatten()
    PropertyRepository.saveProperties(properties)
    println(PropertyRepository.getJsonProperties())
}

private fun calculateDistances() {
    val properties: List<Property> = PropertyRepository.getProperties()
    if (properties.isEmpty()) {
        println("Property cache empty. Fetch properties first")
        return
    }
    val propertiesWithDistance = properties.map {
        val distance = calculateDistance(it)
        println("${it.url} $distance")
        PropertyWithDistance(it, distance ?: MISSING_VALUE)
    }
    PropertyRepository.savePropertiesWithDistance(propertiesWithDistance)
}

private fun calculateDistance(property: Property): String? {
    return property.location?.let { location: LonLat ->
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?origin=${location.longitude},${location.latitude}&destination=$WHOLE_FOODS_COORDS&key=$GOOGLE_MAPS_API_KEY&mode=transit&transit_routing_preference=fewer_transfers"
        val json = callGet(url)
        val response = gson.fromJson(json, DirectionsResponse::class.java)
        response.routes.map {
            val totalDurationSecs: Int = it.legs.map { it.duration.value }.sum()
            val totalDurationMins = TimeUnit.SECONDS.toMinutes(totalDurationSecs.toLong()).toInt()
            val totalDistanceMeters: Int = it.legs.map { it.distance.value }.sum()
            val totalDistanceKm = totalDistanceMeters / 1000
            "${it.legs.size} change(s) in $totalDurationMins minutes ($totalDistanceKm km)"
        }.joinToString(", ")
    }
}

private fun addPropertyToPreferences(url: String) {
    ensureBrowser()
    Scrape.scrapeProperty(driver, propertyUrl = url)?.let { property ->
        val distance: String? = calculateDistance(property)
        val propertyWithDistance = PropertyWithDistance(property, distance ?: MISSING_VALUE)
        val properties = PropertyRepository.getProperties().toMutableList()
        properties.removeIf { it.url == url }
        properties.add(property)
        PropertyRepository.saveProperties(properties)
        val propertiesWithDistance = PropertyRepository.getPropertiesWithDistance().toMutableList()
        propertiesWithDistance.removeIf { it.url == url }
        propertiesWithDistance.add(propertyWithDistance)
        PropertyRepository.savePropertiesWithDistance(propertiesWithDistance)
    }
}

private fun deleteProperty(url: String) {
    val properties = PropertyRepository.getProperties().toMutableList()
    properties.removeIf { it.url == url }
    PropertyRepository.saveProperties(properties)
    val propertiesWithDistance = PropertyRepository.getPropertiesWithDistance().toMutableList()
    propertiesWithDistance.removeIf { it.url == url }
    PropertyRepository.savePropertiesWithDistance(propertiesWithDistance)
}

private fun ensureBrowser() {
    if (!::driver.isInitialized) {
        System.setProperty(
            "webdriver.chrome.driver",
            Paths.get("src/main/resources/chromedriver_win32/chromedriver.exe").toString()
        )
        driver = ChromeDriver(
            ChromeOptions().apply { addArguments("start-maximized") }
        )

        driver[ROOT_URL]
        Preferences.get(PREF_COOKIES, null)?.let {
            runCatching {
                val cookies: Cookies = gson.fromJson(it, Cookies::class.java)
                cookies.cookies.forEach { driver.manage().addCookie(it) }
                driver[ROOT_URL]
            }
        }

        if (runCatching { driver.findElement(By.linkText("Log In")) }.isSuccess) {
            login(driver)
        }
    }
}

private fun cleanupBrowser() {
    if (::driver.isInitialized) {
        driver.quit()
    }
}

private fun login(driver: WebDriver) {
    driver.findElement(By.id("show-user-auth-popup")).click()
    driver.findElement(By.id("loginemail")).click()
    driver.findElement(By.id("loginemail")).sendKeys("gabor.biro@yahoo.com")
    driver.findElement(By.id("loginpass")).click()
    driver.findElement(By.id("loginpass")).sendKeys("6euBDNW9JUssLwy")
    driver.findElement(By.id("sign-in-button")).click()

    val cookies = Cookies(driver.manage().cookies)
    Preferences.save(PREF_COOKIES, gson.toJson(cookies))
}

private const val PREF_COOKIES = "cookies"


private class Command(
    val name: String,
    val exec: () -> Unit
)

private const val EXIT_COMMAND_CODE = "0"

private fun printCommands(commands: Map<String, Command>) {
    println("Available commands:")
    commands.forEach {
        println("${it.key} - ${it.value.name}")
    }
}