package app.gaborbiro.sparescrape.data.model

class LonLat(
    val longitude: String,
    val latitude: String
) {
    override fun toString(): String {
        return "http://www.google.com/maps/place/$longitude,$latitude"
    }

    fun toGoogleApiString()  = "$longitude,$latitude"
}