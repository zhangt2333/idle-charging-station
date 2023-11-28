package model

data class Outlet(
    val no: String,
    val name: String,
    val status: ChargingStation.Status,
    val station: Station,
)
