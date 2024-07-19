package model

import util.TimeUtils

data class Outlet(
    val outletName: String,
    val stationId: Int,
    val status: Status,
    val power: Int,
    val usedMinutes: Int,
    val totalMinutes: Int,
) {

    val remainingMinutes: Int
        get() = totalMinutes - usedMinutes

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

    enum class Status(val code: Int, val msg: String, val totalMinutes: Int) {

        AVAILABLE(1, "空闲中", 0),
        USING(2, "使用中", 999),
        UNAVAILABLE(3, "维护中", 1000),

        ;
    }
}
