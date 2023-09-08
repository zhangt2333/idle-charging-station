package model

import util.TimeUtils

data class ChargingStation(
    val stationName: String,
    val outletName: String,
    val area: String,
    val status: Status,
    val power: Int,
    val usedMinutes: Int,
    val totalMinutes: Int,
) {

    val remainingMinutes: Int
        get() = if (status !== Status.UNAVAILABLE) {
            totalMinutes - usedMinutes
        } else 999

    val usedAndTotalMinutesDesc: String
        get() = if (status === Status.USING) {
            "${usedMinutes}/${totalMinutes}分钟"
        } else ""

    val remainingTimeDesc: String
        get() = if (status === Status.USING) {
            "${remainingMinutes / 60}小时${remainingMinutes % 60}分钟"
        } else ""

    val endTimeDesc: String
        get() = if (status === Status.USING) {
            TimeUtils.getDatetime(fmt = "yyyy-MM-dd HH:mm", deltaMinutes = remainingMinutes)
        } else ""

    val note: String
        get() = if (status === Status.UNAVAILABLE) {
            status.msg
        } else ""

    data class Builder(
        var stationName: String? = null,
        var outletName: String? = null,
        var area: String? = null,
        var status: Status = Status.AVAILABLE,
        var power: Int = 0,
        var usedMinutes: Int = 0,
        var totalMinutes: Int = 0,
    ) {

        fun stationName(stationName: String) = apply { this.stationName = stationName }
        fun outletName(outletName: String) = apply { this.outletName = outletName }
        fun area(area: String) = apply { this.area = area }
        fun status(status: Status) = apply { this.status = status }
        fun power(power: Int) = apply { this.power = power }
        fun usedMinutes(usedMinutes: Int) = apply { this.usedMinutes = usedMinutes }
        fun totalMinutes(totalMinutes: Int) = apply { this.totalMinutes = totalMinutes }

        fun build(): ChargingStation {
            return ChargingStation(
                stationName = stationName!!,
                outletName = outletName!!,
                area = area!!,
                status = status,
                power = power,
                usedMinutes = usedMinutes,
                totalMinutes = totalMinutes,
            )
        }
    }

    enum class Status(val msg: String) {
        USING("使用中"),
        AVAILABLE("空闲中"),
        UNAVAILABLE("维护中"),
        ;
    }
}
