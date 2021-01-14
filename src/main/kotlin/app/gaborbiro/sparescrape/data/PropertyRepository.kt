package app.gaborbiro.sparescrape.data

import app.gaborbiro.sparescrape.data.model.Message
import app.gaborbiro.sparescrape.data.model.Property
import app.gaborbiro.sparescrape.data.model.PropertyWithDistance
import com.google.gson.Gson

object PropertyRepository {

    private val gson = Gson()

    // START messages

    fun getJsonMessages(): String? {
        return Preferences.get(PREF_MESSAGES, null)
    }

    fun getMessages(): List<Message> {
        val jsonMessages = getJsonMessages() ?: return emptyList()
        return gson.fromJson(jsonMessages, MessagesWrapper::class.java).data
    }

    fun saveMessages(messages: List<Message>) {
        val jsonMessages = gson.toJson(MessagesWrapper(messages))
        Preferences.save(PREF_MESSAGES, jsonMessages)
    }

    fun clearMessages() {
        Preferences.clear(PREF_MESSAGES)
    }

    // END messages

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

    fun getJsonPropertiesWithDistances(): String? {
        return Preferences.get(PREF_PROPERTIES_WITH_DISTANCE, null)
    }

    fun getPropertiesWithDistances(): List<PropertyWithDistance> {
        val jsonProperties = getJsonPropertiesWithDistances() ?: return emptyList()
        return gson.fromJson(jsonProperties, PropertiesWithDistanceWrapper::class.java).data
    }

    fun savePropertiesWithDistances(properties: List<PropertyWithDistance>) {
        val jsonProperties = gson.toJson(PropertiesWithDistanceWrapper(properties))
        Preferences.save(PREF_PROPERTIES_WITH_DISTANCE, jsonProperties)
    }

    fun clearPropertiesWithDistances() {
        Preferences.clear(PREF_PROPERTIES_WITH_DISTANCE)
    }

    // END properties with distances calculated
}

private const val PREF_MESSAGES = "messages"
private const val PREF_PROPERTIES = "properties"
private const val PREF_PROPERTIES_WITH_DISTANCE = "properties_with_distance"

private class MessagesWrapper(
    val data: List<Message>
)

private class PropertiesWrapper(
    val data: List<Property>
)

private class PropertiesWithDistanceWrapper(
    val data: List<PropertyWithDistance>
)