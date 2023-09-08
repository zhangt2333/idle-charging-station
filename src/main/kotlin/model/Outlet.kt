package model

data class Outlet(
    val no: String,
    val name: String,
    val status: Int,
    val station: Station,
)
