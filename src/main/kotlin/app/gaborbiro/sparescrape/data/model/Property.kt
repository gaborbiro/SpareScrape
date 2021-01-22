package app.gaborbiro.sparescrape.data.model

import app.gaborbiro.sparescrape.MAX_DISTANCE_MIN
import app.gaborbiro.sparescrape.destinations

open class Property(
    val url: String,
    val title: String,
    val unsuitable: Boolean,
    val senderName: String?,
    val messageUrl: String?,
    val prices: Array<Price>,
    val billIncluded: String,
    val deposit: String,
    val available: String,
    val minTerm: String,
    val maxTerm: String,
    val furnishings: String,
    val broadband: String,
    val livingRoom: String,
    val flatmates: String,
    val totalRooms: String,
    val householdGender: String,
    val preferredGender: String,
    val occupation: String,
    val location: LonLat?
) {
    /**
     * Warning: [senderName] and [messageUrl] cannot be deleted by passing null
     */
    fun clone(
        url: String? = null,
        title: String? = null,
        unsuitable: Boolean? = null,
        senderName: String? = null,
        messageUrl: String? = null,
        prices: Array<Price>? = null,
        billIncluded: String? = null,
        deposit: String? = null,
        available: String? = null,
        minTerm: String? = null,
        maxTerm: String? = null,
        furnishings: String? = null,
        broadband: String? = null,
        livingRoom: String? = null,
        flatmates: String? = null,
        totalRooms: String? = null,
        householdGender: String? = null,
        preferredGender: String? = null,
        occupation: String? = null,
        location: LonLat? = null
    ): Property {
        return Property(
            url = url ?: this.url,
            title = title ?: this.title,
            unsuitable = unsuitable ?: this.unsuitable,
            senderName = senderName ?: this.senderName,
            messageUrl = messageUrl ?: this.messageUrl,
            prices = prices ?: this.prices,
            billIncluded = billIncluded ?: this.billIncluded,
            deposit = deposit ?: this.deposit,
            available = available ?: this.available,
            minTerm = minTerm ?: this.minTerm,
            maxTerm = maxTerm ?: this.maxTerm,
            furnishings = furnishings ?: this.furnishings,
            broadband = broadband ?: this.broadband,
            livingRoom = livingRoom ?: this.livingRoom,
            flatmates = flatmates ?: this.flatmates,
            totalRooms = totalRooms ?: this.totalRooms,
            householdGender = householdGender ?: this.householdGender,
            preferredGender = preferredGender ?: this.preferredGender,
            occupation = occupation ?: this.occupation,
            location = location ?: this.location
        )
    }
}

class PropertyWithDistance(
    val index: Int,
    url: String,
    title: String,
    unsuitable: Boolean,
    senderName: String?,
    messageUrl: String?,
    prices: Array<Price>,
    billIncluded: String,
    deposit: String,
    available: String,
    minTerm: String,
    maxTerm: String,
    furnishings: String,
    broadband: String,
    livingRoom: String,
    flatmates: String,
    totalRooms: String,
    householdGender: String,
    preferredGender: String,
    occupation: String,
    location: LonLat?,
    val distances: List<Distance>
) : Property(
    url = url,
    title = title,
    unsuitable = unsuitable,
    senderName = senderName,
    messageUrl = messageUrl,
    prices = prices,
    billIncluded = billIncluded,
    deposit = deposit,
    available = available,
    minTerm = minTerm,
    maxTerm = maxTerm,
    furnishings = furnishings,
    broadband = broadband,
    livingRoom = livingRoom,
    flatmates = flatmates,
    totalRooms = totalRooms,
    householdGender = householdGender,
    preferredGender = preferredGender,
    occupation = occupation,
    location = location
), Comparable<PropertyWithDistance> {

    constructor(index: Int, property: Property, distances: List<Distance>) : this(
        index = index,
        url = property.url,
        title = property.title,
        unsuitable = property.unsuitable,
        senderName = property.senderName,
        messageUrl = property.messageUrl,
        prices = property.prices,
        billIncluded = property.billIncluded,
        deposit = property.deposit,
        available = property.available,
        minTerm = property.minTerm,
        maxTerm = property.maxTerm,
        furnishings = property.furnishings,
        broadband = property.broadband,
        livingRoom = property.livingRoom,
        flatmates = property.flatmates,
        totalRooms = property.totalRooms,
        householdGender = property.householdGender,
        preferredGender = property.preferredGender,
        occupation = property.occupation,
        location = property.location,
        distances = distances
    )

    override fun compareTo(other: PropertyWithDistance): Int {
        return averageScore().compareTo(other.averageScore())
    }

    fun averageScore(): Int {
        return this.distances.map {
            val bestRoute = it.routes.minByOrNull { it.timeMinutes }!!
            (MAX_DISTANCE_MIN - bestRoute.timeMinutes) * destinations[it.destination]!!.second
        }.average().toInt()
    }
}

class Price(
    val price: String,
    val pricePerMonth: String,
    val pricePerMonthInt: Int
) {
    override fun toString(): String {
        return pricePerMonth + price.let { if (it != pricePerMonth) " ($price)" else "" }
    }
}