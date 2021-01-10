package app.gaborbiro.sparescrape.data

import app.gaborbiro.sparescrape.data.model.*
import com.google.gson.Gson

object PropertyRepository {

    private val gson = Gson()

    // START message links

    fun getJsonLinks(): String? {
        return Preferences.get(PREF_LINKS, null)
    }

    fun getLinks(): List<MessageLinkSet> {
        val jsonLinks = Preferences.get(PREF_LINKS, null) ?: return emptyList()
        return gson.fromJson(jsonLinks, MessageLinksWrapper::class.java).data
    }

    fun saveLinks(messageLinks: List<MessageLinkSet>) {
        val jsonLinks = gson.toJson(MessageLinksWrapper(messageLinks))
        Preferences.save(PREF_LINKS, jsonLinks)
    }

    fun clearLinks() {
        Preferences.clear(PREF_LINKS)
    }

    // END message links

    // START Properties

    fun getJsonProperties(): String? {
        return Preferences.get(PREF_PROPERTIES, null)
    }

    fun getProperties(): List<Property> {
        val jsonProperties = getJsonProperties() ?: return emptyList()
        return gson.fromJson(jsonProperties, PropertiesWrapper::class.java).data
    }

    fun saveProperties(properties: List<Property>) {
        val jsonProperties = gson.toJson(PropertiesWrapper(properties))
        Preferences.save(PREF_PROPERTIES, jsonProperties)
    }

    fun clearProperties() {
        Preferences.clear(PREF_PROPERTIES)
    }

    // END Properties

    // START properties with distances calculated

    fun getJsonPropertiesWithDistance(): String? {
        return Preferences.get(PREF_PROPERTIES_WITH_DISTANCE, null)
    }

    fun getPropertiesWithDistance(): List<PropertyWithDistance> {
        val jsonProperties = getJsonPropertiesWithDistance() ?: return emptyList()
        return gson.fromJson(jsonProperties, PropertiesWithDistanceWrapper::class.java).data
    }

    fun savePropertiesWithDistance(properties: List<PropertyWithDistance>) {
        val jsonProperties = gson.toJson(PropertiesWithDistanceWrapper(properties))
        Preferences.save(PREF_PROPERTIES_WITH_DISTANCE, jsonProperties)
    }

    fun clearPropertiesWithDistance() {
        Preferences.clear(PREF_PROPERTIES_WITH_DISTANCE)
    }

    // END properties with distances calculated
}

private const val PREF_LINKS = "links"
private const val PREF_PROPERTIES = "properties"
private const val PREF_PROPERTIES_WITH_DISTANCE = "properties_with_distance"

private class MessageLinksWrapper(
    val data : List<MessageLinkSet>
)

private class PropertiesWrapper(
    val data: List<Property>
)

private class PropertiesWithDistanceWrapper(
    val data: List<PropertyWithDistance>
)