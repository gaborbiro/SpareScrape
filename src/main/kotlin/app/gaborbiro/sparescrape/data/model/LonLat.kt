package app.gaborbiro.sparescrape.data.model

class LonLat(
    val longitude: String,
    val latitude: String
) {
    override fun toString(): String {
        return "(longitude='$longitude', latitude='$latitude')"
    }
}