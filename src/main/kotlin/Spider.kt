import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import model.ChargingStation
import model.Outlet
import model.Station
import util.OkHttpUtils
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import util.JsonUtils
import util.TimeUtils
import util.syncGetJson
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

class Spider {

    private val token = System.getenv("token")
        ?: File("token.json").readTextOrNull()?.trim()
        ?: ""

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

    private fun File.readTextOrNull(charset: java.nio.charset.Charset = Charsets.UTF_8): String? {
        return try {
            this.readText(charset)
        } catch (e: Exception) {
            null
        }
    }

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
        val status = if (json.get("data").get("station").get("hardWareState").asInt() == 1) {
            outlet.status
        } else {
            ChargingStation.Status.UNAVAILABLE
        }
        val totalMin = when (status) {
            ChargingStation.Status.AVAILABLE -> 0
            ChargingStation.Status.USING -> if (remainingMin > 0) usedMin + remainingMin else 999
            ChargingStation.Status.UNAVAILABLE -> 1000
        }

        val power = json.get("powerFee")?.get("billingPower")?.asText()?.extractDigit() ?: 0
        return ChargingStation(
            stationName = outlet.station.name,
            outletName = outlet.name,
            area = outlet.station.area,
            status = status,
            power = power,
            usedMinutes = usedMin,
            totalMinutes = totalMin,
        )
    }

}

fun main() {
    val stations = JsonUtils.toJson(File("stations.json").readText())
        ?.map {
            Station(
                it.get("id").asInt(),
                it.get("name").asText(),
                it.get("area").asText(),
            )
        }
        ?: error("Read stations.json failed.")

    val results: MutableList<ChargingStation> = CopyOnWriteArrayList()
    runBlocking {
        val spider = Spider()
        if (!spider.checkAlive()) {
            return@runBlocking
        }
        for (station in stations) {
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
        head {
            title("南哪儿充不了电")
        }
        body {
            style {
                +"table, th, td { border: 1px solid; }"
            }
            h1 { +"更新时间：${TimeUtils.getDatetime()}" }
            p { +"status:${if (results.isNotEmpty()) "up" else "down"}" }
            for (area in stations.map { it.area }.distinct()) {
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

    if (results.isNotEmpty()) {
        File("build/data/${System.currentTimeMillis()}.data").let {
            it.parentFile.mkdirs()
            it.writeText(text = JsonUtils.toJsonString(results)!!)
        }
    }

    File("build/html/index.html").let {
        it.parentFile.mkdirs()
        it.writeText(html)
    }
}
