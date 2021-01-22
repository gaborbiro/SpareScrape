package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.PropertyRepository
import app.gaborbiro.sparescrape.data.model.*
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern


fun main(args: Array<String>) {
    SpareScrape().main(args)
}

class SpareScrape {

    private val gson = Gson()
    private lateinit var commands: Map<String, Command>

    fun main(args: Array<String>) {
        BufferedReader(InputStreamReader(System.`in`)).use { reader ->
            commands = setupCommandLineCommands(reader)
            var command: String?
            println()
            printCommands(commands)
            do {
                println("==========================================================================")
                print("> ")
                command = reader.readLine()
                if (command == EXIT_COMMAND_CODE) {
                    return@use
                }
                val tokens = command.trim().split(Regex("[\\s]+"))
                var tokenCount = tokens.size
                var candidateCount = 0
                var matcher: Matcher
                var solution: String? = null
                var candidate: String?
                do {
                    candidate = tokens.take(tokenCount).joinToString(" ")

                    val candidatePattern = Pattern.compile("^${candidate}([^\\s]*)")
                    candidateCount = commands.keys.filter { key ->
                        matcher = candidatePattern.matcher(key)
                        if (matcher.find()) {
                            solution = key
                            true
                        } else {
                            false
                        }
                    }.size
                    tokenCount--
                } while (candidateCount != 1 && tokenCount > 0)

                if (candidateCount > 1) {
                    val exactMatch = commands.keys.firstOrNull { candidate == it }
                    if (exactMatch != null) {
                        val args = command.removePrefix(exactMatch).trim().split(Regex("[\\s]+"))
                        executeCommand(exactMatch, args)
                    } else {
                        println("Invalid command")
                    }
                } else if (candidateCount == 1) {
                    val args = command.removePrefix(candidate!!).trim().split(Regex("[\\s]+"))
                    executeCommand(solution!!, args)
                } else {
                    println("Invalid command")
                }
            } while (command != EXIT_COMMAND_CODE)
        }

        Scrape.cleanup()
    }

    private fun executeCommand(command: String, args: List<String>) {
        println(command + " args:" + args.joinToString(","))
        runCatching { commands[command]!!.exec.invoke(args) }.exceptionOrNull()?.printStackTrace()
    }

    private fun setupCommandLineCommands(reader: BufferedReader): Map<String, Command> {
        return mutableMapOf(
            "help" to Command("print this menu") {
                printCommands(commands)
            },
            "full" to Command("wipe database, get messages, scrape properties and calculate distances") {
                PropertyRepository.clearMessages()
                PropertyRepository.clearProperties()
                PropertyRepository.clearPropertiesWithDistances()
                scrapeMessages()
                scrapeProperties()
                calculateDistances()
            },
            "get messages" to Command("scrape inbox (cached)") {
                scrapeMessages()
            },
            "get properties" to Command("scrape links for property data (new only)") {
                scrapeProperties()
            },
            "get distances" to Command("calculate distances (new only)") {
                calculateDistances()
            },
            "list messages" to Command("print messages") {
                println(PropertyRepository.getJsonMessages())
            },
            "list properties" to Command("print properties") {
                println(PropertyRepository
                    .getProperties()
                    .joinToString("\n\n") { it.prettyPrint() })
            },
            "list distances" to Command("print properties with distances calculated") {
                println(PropertyRepository
                    .getPropertiesWithDistances().sortedDescending()
                    .forEach {
                        println()
                        println(it.prettyPrint())
                    })
            },
            "clear" to Command("delete all data") {
                PropertyRepository.clearMessages()
                PropertyRepository.clearProperties()
                PropertyRepository.clearPropertiesWithDistances()
            },
            "clear messages" to Command("delete messages") {
                PropertyRepository.clearMessages()
            },
            "clear properties" to Command("delete properties") {
                PropertyRepository.clearProperties()
            },
            "clear distances" to Command("delete properties with distances") {
                PropertyRepository.clearPropertiesWithDistances()
            },
            "clear session" to Command("delete session cookies (will re-login)") {
                Scrape.clearCookies()
            },
            "cls" to Command("clear console") {
                ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor()
            },
            "scrape" to Command("scrape property, display results and open in browser (same as scan)") {
                val property = if (it.size > 0) {
                    scrapeProperty(cleanUrl(it[0]), save = false)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    scrapeProperty(cleanUrl(url), save = false)
                }
                property?.openLinks()
            },
            "scan" to Command("scrape property, display results and open in browser (same as scrape)") {
                val property = if (it.size > 0) {
                    scrapeProperty(cleanUrl(it[0]), save = false)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    scrapeProperty(cleanUrl(url), save = false)
                }
                property?.openLinks()
            },
            "add" to Command("scrape property, display results and open in browser (same as scrape and scan, but also saves or marks the property)") {
                val property = if (it.size > 0) {
                    scrapeProperty(cleanUrl(it[0]), save = true)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    scrapeProperty(cleanUrl(url), save = true)
                }
                property?.openLinks()
            },
            "open" to Command("Open property (index) in browser") {
                val index = if (it.size > 0) {
                    it[0]
                } else {
                    print("index: ")
                    reader.readLine()
                }
                runCatching { index.toInt() }.getOrNull()?.let { index ->
                    PropertyRepository.getPropertiesWithDistances().firstOrNull { it.index == index }
                        ?.openLinks()
                        ?: run { println("Cannot find property with index $index") }
                } ?: println("Invalid index '$index'")
            },
            "delete" to Command("delete a property (by url) from the database") {
                if (it.size > 0) {
                    deleteProperty(propertyLink = it[0], markAsUnsuitable = false)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    deleteProperty(url, markAsUnsuitable = false)
                }
            },
            "delete index" to Command("delete a property from the database") {
                val index = if (it.size > 0) {
                    it[0]
                } else {
                    print("index: ")
                    reader.readLine()
                }
                runCatching { index.toInt() }.getOrNull()?.let { index ->
                    PropertyRepository.getPropertiesWithDistances().firstOrNull { it.index == index }
                        ?.run { deleteProperty(this.url, markAsUnsuitable = false) }
                        ?: run { println("Cannot find property with index $index") }
                } ?: println("Invalid index '$index'")
            },
            "mark" to Command("delete property (by url) and mark it as unsuitable") {
                if (it.size > 0) {
                    deleteProperty(it[0], markAsUnsuitable = true)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    deleteProperty(url, markAsUnsuitable = true)
                }
            },
            "mark index" to Command("delete property and mark it as unsuitable") {
                val index = if (it.size > 0) {
                    it[0]
                } else {
                    print("index: ")
                    reader.readLine()
                }
                runCatching { index.toInt() }.getOrNull()?.let { index ->
                    PropertyRepository.getPropertiesWithDistances().firstOrNull { it.index == index }
                        ?.run { deleteProperty(this.url, markAsUnsuitable = true) }
                        ?: run { println("Cannot find property with index $index") }
                } ?: println("Invalid index '$index'")
            },
            "name" to Command("set sender name for property (by url)") {
                if (it.size > 1) {
                    setSenderName(it[0], it.drop(1).joinToString(" "))
                } else if (it.size > 0) {
                    print("name: ")
                    val name = reader.readLine()
                    setSenderName(it[0], name)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    print("name: ")
                    val name = reader.readLine()
                    setSenderName(url, name)
                }
            },
            "name index" to Command("set sender name for property") {
                val (index, name) = when {
                    it.size > 1 -> {
                        Pair(it[0], it.drop(1).joinToString(" "))
                    }
                    it.size > 0 -> {
                        print("name: ")
                        val name = reader.readLine()
                        Pair(it[0], name)
                    }
                    else -> {
                        print("index: ")
                        val index = reader.readLine()
                        print("name: ")
                        val name = reader.readLine()
                        Pair(index, name)
                    }
                }
                runCatching { index.toInt() }.getOrNull()?.let { index ->
                    PropertyRepository.getPropertiesWithDistances().firstOrNull { it.index == index }
                        ?.run { setSenderName(url, name) }
                        ?: run { println("Cannot find property with index $index") }
                } ?: println("Invalid index '$index'")
            },
            "search" to Command("Scrape a search result. Prints good matches only") {
                if (it.size > 0) {
                    scrapeSearch(it[0])
                } else {
                    print("page url: ")
                    val url = reader.readLine()
                    scrapeSearch(cleanUrl(url))
                }
            },
        ).let {
            if (it.containsKey(EXIT_COMMAND_CODE)) {
                throw IllegalStateException("Command code $EXIT_COMMAND_CODE is reserved!")
            }
            it[EXIT_COMMAND_CODE] = Command("quit") {}
            it.toMap()
        }
    }

    private fun setSenderName(url: String, name: String) {
        val properties = PropertyRepository.getProperties().toMutableList()
        properties.forEachIndexed { index, property ->
            if (cleanUrl(property.url) == cleanUrl(url)) {
                properties[index] = property.clone(senderName = name)
            }
        }
        PropertyRepository.saveProperties(properties)

        val propertiesWithDistances = PropertyRepository.getPropertiesWithDistances().toMutableList()
        propertiesWithDistances.forEachIndexed { index, propertyWithDistance ->
            if (cleanUrl(propertyWithDistance.url) == cleanUrl(url)) {
                propertiesWithDistances[index] =
                    PropertyWithDistance(
                        propertyWithDistance.index,
                        propertyWithDistance.clone(senderName = name),
                        propertyWithDistance.distances
                    )
            }
        }
        PropertyRepository.savePropertiesWithDistances(propertiesWithDistances)
    }

    private fun scrapeSearch(url: String) {
        val propertyLinks = Scrape.scrapeSearch(url)
        propertyLinks.forEach { propertyUrl ->
            println()
            val property = Scrape.scrapeProperty(propertyUrl, senderName = null, messageUrl = null)
            println("Scraping ${property.title} $propertyUrl")
            // pre-validate to save on the Google API calls
            if (!validate(property)) {
                if (!property.unsuitable) {
                    Scrape.markAsUnsuitable(url, openUrl = true)
                }
            } else {
                val distances = calculateDistances(property)
                val propertyWithDistance = PropertyWithDistance(PropertyRepository.getNextIndex(), property, distances)
                if (validate(propertyWithDistance)) {
                    saveProperty(property, propertyWithDistance)
                    println()
                    println(propertyWithDistance.prettyPrint())
                } else {
                    Scrape.markAsUnsuitable(propertyUrl, openUrl = false)
                }
            }
        }
    }

    private fun scrapeMessages() {
        val messages = Scrape.scrapeMessages()
        PropertyRepository.saveMessages(messages)
    }

    /**
     * Grab saved messages. Scrape each property link in a message. Marks message if needed.
     */
    private fun scrapeProperties() {
        val messages: List<Message> = PropertyRepository.getMessages()
        if (messages.isEmpty()) {
            println("No messages. Fetch inbox first or scrape a search result.")
            return
        }
        val properties = PropertyRepository.getProperties().toMutableList()
        val savedPropertyLinks: Map<String, Property> = properties.associateBy { it.url }
        messages.forEach { message ->
            scrapeMessage(message, savedPropertyLinks).also {
                it.forEach { newProperty ->
                    properties.removeIf { it.url == newProperty.url }
                    properties.add(newProperty)
                }
            }
        }
        PropertyRepository.saveProperties(properties)
    }

    private fun scrapeMessage(
        message: Message,
        savedPropertyLinks: Map<String, Property>
    ): List<Property> {
        val propertyLinks = message.propertyLinks
        val rejectionMap = propertyLinks.associateWith { false }.toMutableMap()
        val properties = mutableListOf<Property>()
        propertyLinks.forEach { propertyLink ->
            if (isValidUrl(propertyLink)) {
                val property: Property = savedPropertyLinks[propertyLink] ?: run {
                    println("Scraping property $propertyLink (sent by ${message.senderName})")
                    Scrape.scrapeProperty(propertyLink, message.senderName, message.messageLink)
                }
                if (Scrape.checkForBuddyUp()) {
                    Scrape.tagMessage(message.messageLink, openUrl = true, Tag.BUDDY_UP)
                } else if (!validate(property)) {
                    if (!property.unsuitable) { // not yet marked as unsuitable
                        Scrape.markAsUnsuitable(propertyLink, openUrl = false)
                    }
                    rejectionMap[propertyLink] = true
                } else {
                    if (property.prices.isEmpty()) {
                        println("Price missing")
                        Scrape.tagMessage(message.messageLink, openUrl = true, Tag.PRICE_MISSING)
                    } else {
                        properties.add(property)
                    }
                }
            } else {
                println("Not a spareroom url: $propertyLink (${message.senderName})")
            }
        }
        if (rejectionMap.values.any { it }) {
            if (rejectionMap.values.all { it }) {
                Scrape.tagMessage(message.messageLink, openUrl = true, Tag.REJECTED)
            } else if (rejectionMap.values.any { it }) {
                Scrape.tagMessage(message.messageLink, openUrl = true, Tag.PARTIALLY_REJECTED)
                println("Partial rejection:\n" + rejectionMap.map { it.key + " -> " + if (it.value) "rejected" else "fine" }
                    .joinToString("\n"))
            }
        }
        return properties
    }

    /**
     * Grab saved properties. Calculate distances for each of them. Marks message if needed.
     */
    private fun calculateDistances() {
        val properties: List<Property> = PropertyRepository.getProperties()
        if (properties.isEmpty()) {
            println("No saved properties. Do some scraping.")
            return
        }
        val propertiesWithDistances = PropertyRepository.getPropertiesWithDistances().toMutableList()
        val savedPropertyLinks = propertiesWithDistances.associateBy { it.url }
        properties.forEach { property ->
            val propertyWithDistance = savedPropertyLinks[property.url] ?: run {
                val distances = calculateDistances(property)
                println()
                println("${property.senderName} ${property.url} ${distances.joinToString(", ")}")
                PropertyWithDistance(PropertyRepository.getNextIndex(), property, distances)
            }
            if (!validate(propertyWithDistance)) {
                if (!propertyWithDistance.unsuitable) { // not yet marked as unsuitable
                    Scrape.markAsUnsuitable(property.url, openUrl = false)
                }
                property.messageUrl?.let {
                    Scrape.tagMessage(it, openUrl = true, Tag.REJECTED)
                }
            } else {
                propertiesWithDistances.add(propertyWithDistance)
            }
        }
        PropertyRepository.savePropertiesWithDistances(propertiesWithDistances)
    }

    private fun calculateDistances(property: Property): List<Distance> {
        return property.location?.let { location: LonLat ->
            destinations.map {
                val distance = calculateDistance(location, it.value.first)
                Distance(it.key, distance)
            }
        } ?: emptyList()
    }

    private fun calculateDistance(from: LonLat, to: LonLat): List<app.gaborbiro.sparescrape.data.model.Route> {
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${from.toGoogleApiString()}&" +
                    "destination=${to.toGoogleApiString()}&" +
                    "key=$GOOGLE_MAPS_API_KEY&" +
                    "mode=transit&" +
                    "transit_routing_preference=fewer_transfers"
        val json = callGet(url)
        val response = gson.fromJson(json, DirectionsResponse::class.java)
        return response.routes.map {
            val totalDurationSecs: Int = it.legs.map { it.duration.value }.sum()
            val totalDurationMins = TimeUnit.SECONDS.toMinutes(totalDurationSecs.toLong()).toInt()
            val totalDistanceMeters: Int = it.legs.map { it.distance.value }.sum()
            val totalDistanceKm = totalDistanceMeters / 1000
            Route(it.legs.size, totalDurationMins, totalDistanceKm)
        }
    }

    /**
     * Ad-hoc scan of a property. Marks property as unsuitable if needed.
     */
    private fun scrapeProperty(propertyLink: String, save: Boolean): PropertyWithDistance? {
        var finalLink: String? = null
        if (!isValidUrl(propertyLink)) {
            val matcher = propertyLink.matcher("([\\d]+)")
            if (matcher.find()) {
                finalLink = "$ROOT_URL/$propertyLink"
            }
        } else {
            finalLink = propertyLink
        }
        println()
        if (finalLink == null) {
            println("Invalid link: $finalLink")
        } else {
            val property = Scrape.scrapeProperty(finalLink, senderName = null, messageUrl = null)
            val distances = calculateDistances(property)
            val propertyWithDistance = PropertyWithDistance(PropertyRepository.getNextIndex(), property, distances)
            println(propertyWithDistance.prettyPrint())
            if (save) {
                if (validate(property)) {
                    saveProperty(property, propertyWithDistance)
                } else if (!property.unsuitable) {
                    Scrape.markAsUnsuitable(finalLink, openUrl = false)
                }
            }
            return propertyWithDistance
        }
        return null
    }

    private fun saveProperty(property: Property, propertyWithDistance: PropertyWithDistance) {
        val properties = PropertyRepository.getProperties().toMutableList()
        var index = properties.indexOfFirst { cleanUrl(it.url) == property.url }
        if (index > -1) {
            properties[index] = property
        } else {
            properties.add(property)
        }
        PropertyRepository.saveProperties(properties)

        val propertiesWithDistance = PropertyRepository.getPropertiesWithDistances().toMutableList()
        index = propertiesWithDistance.indexOfFirst { cleanUrl(it.url) == propertyWithDistance.url }
        if (index > -1) {
            propertiesWithDistance[index] = propertyWithDistance
        } else {
            propertiesWithDistance.add(propertyWithDistance)
        }
        PropertyRepository.savePropertiesWithDistances(propertiesWithDistance)
    }

    private fun deleteProperty(propertyLink: String, markAsUnsuitable: Boolean) {
        val messages = PropertyRepository.getMessages().toMutableList()
        val cleanedMessages = messages.mapNotNull {
            val propertyLinks = it.propertyLinks.toMutableList()
            if (propertyLinks.removeIf { it == propertyLink }) {
                println("Property removed")
            }
            if (propertyLinks.isNotEmpty()) {
                Message(it.senderName, it.messageLink, propertyLinks)
            } else {
                null
            }
        }
        PropertyRepository.saveMessages(cleanedMessages)
        val properties = PropertyRepository.getProperties().toMutableList()
        if (properties.removeIf { it.url == propertyLink }) {
            println("Property removed")
        }
        PropertyRepository.saveProperties(properties)
        val propertiesWithDistance = PropertyRepository.getPropertiesWithDistances().toMutableList()
        if (propertiesWithDistance.removeIf { it.url == propertyLink }) {
            println("Property with distances removed")
        }
        PropertyRepository.savePropertiesWithDistances(propertiesWithDistance)
        if (markAsUnsuitable) {
            Scrape.markAsUnsuitable(propertyLink, true)
        }
    }

    private class Command(
        val name: String,
        val exec: (args: List<String>) -> Unit
    )

    private fun printCommands(commands: Map<String, Command>) {
        println("Available commands:")
        println(commands.mapValues { it.value.name }.prettyPrint())
    }
}

private const val EXIT_COMMAND_CODE = "quit"

private fun PropertyWithDistance.openLinks() {
    messageUrl?.let {
        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler $it $url")
    }
    Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler $url")
    location?.let {
        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler $it")
    }
}

