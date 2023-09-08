package util

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimeUtils {

    fun getDatetime(
        fmt: String = "yyyy-MM-dd HH:mm:ss",
        timeZone: Int = 8,
        deltaMinutes: Int = 0,
    ): String {
        val utcDateTime = LocalDateTime.now(ZoneOffset.UTC)
        val targetDateTime = utcDateTime.plusHours(timeZone.toLong())
            .plusMinutes(deltaMinutes.toLong())
        val formatter = DateTimeFormatter.ofPattern(fmt)
        return formatter.format(targetDateTime)
    }

}
