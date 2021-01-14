package app.gaborbiro.sparescrape.data.model

import app.gaborbiro.sparescrape.addPadding

class Distance(
    val destination: String,
    val routes: List<Route>
) {

    override fun toString(): String {
        return "\n${
            addPadding(destination, 11)
        } => ${routes.sortedBy { it.changes }.joinToString(", ")})"
    }
}

class Route(
    val changes: Int,
    val timeMinutes: Int,
    val distanceKm: Int
) {
    override fun toString(): String {
        return "$changes change(s) in $timeMinutes minutes ($distanceKm km)"
    }
}