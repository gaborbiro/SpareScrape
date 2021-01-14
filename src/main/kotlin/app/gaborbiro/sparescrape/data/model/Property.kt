package app.gaborbiro.sparescrape.data.model

open class Property(
    val url: String,
    val unsuitable: Boolean,
    val senderName: String?,
    val messageUrl: String?,
    val price: String,
    val pricePerMonth: String,
    val pricePerMonthInt: Int,
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
        unsuitable: Boolean? = null,
        senderName: String? = null,
        messageUrl: String? = null,
        price: String? = null,
        pricePerMonth: String? = null,
        pricePerMonthInt: Int? = null,
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
            unsuitable = unsuitable ?: this.unsuitable,
            senderName = senderName ?: this.senderName,
            messageUrl = messageUrl ?: this.messageUrl,
            price = price ?: this.price,
            pricePerMonth = pricePerMonth ?: this.pricePerMonth,
            pricePerMonthInt = pricePerMonthInt ?: this.pricePerMonthInt,
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
    url: String,
    unsuitable: Boolean,
    senderName: String?,
    messageUrl: String?,
    price: String,
    pricePerMonth: String,
    pricePerMonthInt: Int,
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
    unsuitable = unsuitable,
    senderName = senderName,
    messageUrl = messageUrl,
    price = price,
    pricePerMonth = pricePerMonth,
    pricePerMonthInt = pricePerMonthInt,
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

    constructor(property: Property, distances: List<Distance>) : this(
        url = property.url,
        unsuitable = property.unsuitable,
        senderName = property.senderName,
        messageUrl = property.messageUrl,
        price = property.price,
        pricePerMonth = property.pricePerMonth,
        pricePerMonthInt = property.pricePerMonthInt,
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
        return averageDistance().compareTo(other.averageDistance())
    }

    private fun averageDistance() = this.distances.map { it.routes.minByOrNull { it.timeMinutes }!!.timeMinutes }.average().toInt()
}