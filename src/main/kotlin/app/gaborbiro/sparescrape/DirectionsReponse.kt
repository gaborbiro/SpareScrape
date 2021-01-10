package app.gaborbiro.sparescrape

class DirectionsResponse(
    val routes: List<Route>
)

class Route(
    val legs: List<RouteLeg>
)

class RouteLeg(
    val duration: LegMeasurement, // seconds
    val distance: LegMeasurement // meters
)

class LegMeasurement(
    val value: Int
)