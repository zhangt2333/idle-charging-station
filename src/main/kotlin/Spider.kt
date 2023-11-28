import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import model.ChargingStation
import model.Outlet
import model.Station
import util.OkHttpUtils
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import util.TimeUtils
import util.syncGetJson
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

class Spider {

    private val token = System.getenv("token") ?: ""

    private val proxy = System.getenv("HTTP_PROXY")
        ?.let { URI(it) }
        ?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) }
        ?: Proxy.NO_PROXY

    private val headerInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()

        val modifiedRequest = originalRequest.newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) WindowsWechat(0x6307061d)",
            )
            .addHeader("token", token)
            .build()

        chain.proceed(modifiedRequest)
    }

    private val client: OkHttpClient = OkHttpUtils.newBuilder(true)
        .addInterceptor(headerInterceptor)
        .proxy(proxy)
        .build()

    fun checkAlive(): Boolean {
        val json = client.syncGetJson(
            "https://wemp.issks.com/recharge/v1/registerCard/checkSign"
        )
        return json.get("success").asBoolean()
    }

    fun fetchOutletList(station: Station): List<Outlet> {
        val json = client.syncGetJson(
            "https://wemp.issks.com/charge/v1/outlet/station/outlets/${station.id}"
        )
        return (json.get("data") as ArrayNode).map {
            Outlet(
                it.get("outletNo").asText(),
                "插座" + it.get("outletSerialNo").asText(),
                ChargingStation.Status.of(it.get("currentChargingRecordId").asInt()),
                station,
            )
        }
    }

    private fun String.extractDigit(): Int = try {
        this.filter { it.isDigit() }.toInt()
    } catch (ignored: Exception) {
        0
    }

    fun fetchOutletDetail(outlet: Outlet): ChargingStation {
        val json = client.syncGetJson(
            "https://wemp.issks.com/charge/v1/charging/outlet/${outlet.no}"
        )
        val usedMin = json.get("data").get("usedmin").asInt()
        val remainingMin = json.get("data").get("restmin").asInt()
        val totalMin = when (outlet.status) {
            ChargingStation.Status.AVAILABLE -> 0
            ChargingStation.Status.USING -> if (remainingMin > 0) usedMin + remainingMin else 999
            ChargingStation.Status.UNAVAILABLE -> 1000
        }

        val power = json.get("powerFee")?.get("billingPower")?.asText()?.extractDigit() ?: 0
        return ChargingStation(
            stationName = outlet.station.name,
            outletName = outlet.name,
            area = outlet.station.area,
            status = outlet.status,
            power = power,
            usedMinutes = usedMin,
            totalMinutes = totalMin,
        )
    }

}

fun main() {
    val results: MutableList<ChargingStation> = CopyOnWriteArrayList()

    runBlocking {
        val spider = Spider()
        if (!spider.checkAlive()) {
            return@runBlocking
        }
        for (station in Station.stations) {
            launch(Dispatchers.IO) {
                for (outlet in spider.fetchOutletList(station)) {
                    launch(Dispatchers.IO) {
                        val chargingStation = spider.fetchOutletDetail(outlet)
                        results.add(chargingStation)
                    }
                }
            }
        }
    }

    results.sortBy { it.remainingMinutes }

    val html = createHTML().html {
        body {
            style {
                +"table, th, td { border: 1px solid; }"
            }
            h1 { +"更新时间：${TimeUtils.getDatetime()}" }
            for (area in Station.stations.map { it.area }.distinct()) {
                h3 { +area }
                table {
                    thead {
                        tr {
                            th { +"充电桩" }
                            th { +"插座号" }
                            th { +"充电时长" }
                            th { +"剩余时长" }
                            th { +"结束时间" }
                            th { +"备注" }
                        }
                    }
                    tbody {
                        for (result in results.filter { it.area == area }) {
                            tr {
                                td { +result.stationName }
                                td { +result.outletName }
                                td { +result.usedAndTotalMinutesDesc }
                                td { +result.remainingTimeDesc }
                                td { +result.endTimeDesc }
                                td { +result.note }
                            }
                        }
                    }
                }
            }
        }
    }

    File("build/html/index.html").let {
        it.parentFile.mkdirs()
        it.writeText(html)
    }
}
