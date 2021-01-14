package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.PropertyRepository
import app.gaborbiro.sparescrape.data.model.*
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


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
            printCommands(commands)
            do {
                println("============================================================================================================================")
                print("> ")
                command = reader.readLine()
                if (command == EXIT_COMMAND_CODE) {
                    return@use
                }
                val tokens = command.split(" ")
                commands[tokens[0]]
                    ?.exec
                    ?.apply {
                        runCatching { invoke(tokens) }.exceptionOrNull()?.printStackTrace()
                    }
                    ?: run {
                        commands.keys.firstOrNull { it.startsWith(command) }?.apply {
                            commands[this]!!.exec(tokens)
                        } ?: run { println("Invalid command '$command'") }
                    }
            } while (command != EXIT_COMMAND_CODE)
        }

        Scrape.cleanup()
    }

    private fun setupCommandLineCommands(reader: BufferedReader): Map<String, Command> {
        return mutableMapOf(
            "help" to Command("print this menu") {
                printCommands(commands)
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
                    .getPropertiesWithDistances().sorted()
                    .joinToString("\n\n") { it.prettyPrint() })
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
            "scrape" to Command("scrape and display property") {
                if (it.size > 1) {
                    scrapeProperty(cleanUrl(it[1]))
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    scrapeProperty(cleanUrl(url))
                }
            },
            "remove" to Command("remove a property from the database") {
                if (it.size > 1) {
                    deleteProperty(it[1], markAsUnsuitable = false)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    deleteProperty(url, markAsUnsuitable = false)
                }
            },
            "mark" to Command("delete property") {
                if (it.size > 1) {
                    deleteProperty(it[1], markAsUnsuitable = true)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    deleteProperty(url, markAsUnsuitable = true)
                }
            },
            "name" to Command("set sender name") {
                if (it.size > 2) {
                    setSenderName(it[1], it[2])
                    deleteProperty(it[1], markAsUnsuitable = false)
                } else if (it.size > 1) {
                    print("name: ")
                    val name = reader.readLine()
                    setSenderName(it[1], name)
                } else {
                    print("property url: ")
                    val url = reader.readLine()
                    print("name: ")
                    val name = reader.readLine()
                    setSenderName(url, name)
                }
            },
            "search" to Command("scrape search") {
                if (it.size > 1) {
                    scrapeSearch(it[1])
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
        properties.filter { it.url == cleanUrl(url) }.forEachIndexed { index, property ->
            properties[index] = property.clone(senderName = name)
        }
        PropertyRepository.saveProperties(properties)

        val propertiesWithDistances = PropertyRepository.getPropertiesWithDistances().toMutableList()
        propertiesWithDistances.filter { it.url == cleanUrl(url) }.forEachIndexed { index, property ->
            propertiesWithDistances[index] = PropertyWithDistance(property.clone(senderName = name), property.distances)
        }
        PropertyRepository.savePropertiesWithDistances(propertiesWithDistances)
    }

    private fun scrapeSearch(url: String) {
        val propertyLinks = Scrape.scrapeSearch(url)
        propertyLinks.forEach { propertyUrl ->
            val property = Scrape.scrapeProperty(propertyUrl, senderName = null, messageUrl = null)
            // pre-validate to save on the Google API calls
            if (!validate(property)) {
                if (!property.unsuitable) {
                    Scrape.markAsUnsuitable(url, openUrl = true)
                }
            } else {
                val distances = calculateDistances(property)
                val propertyWithDistance = PropertyWithDistance(property, distances)
                if (validate(propertyWithDistance)) {
                    saveProperty(property, propertyWithDistance)
                } else {
                    Scrape.markAsUnsuitable(url, openUrl = true)
                }
                println(propertyWithDistance.prettyPrint())
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
        val savedPropertyLinks = properties.associateBy { it.url }
        messages.forEach { message ->
            val propertyLinks = message.propertyLinks
            val rejectionMap = propertyLinks.associateWith { false }.toMutableMap()
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
                        if (property.price == MISSING_VALUE) {
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
        }
        PropertyRepository.saveProperties(properties)
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
                PropertyWithDistance(property, distances)
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
                val distance = calculateDistance(location, it.value)
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
    private fun scrapeProperty(propertyLink: String) {
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
            val propertyWithDistance = PropertyWithDistance(property, distances)
            if (!validate(property) && !property.unsuitable) {
                Scrape.markAsUnsuitable(finalLink, openUrl = false)
            }
            println(propertyWithDistance.prettyPrint())
        }
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
        commands.forEach {
            println("${it.key} - ${it.value.name}")
        }
    }
}

private const val EXIT_COMMAND_CODE = "quit"

