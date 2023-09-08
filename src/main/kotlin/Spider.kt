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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import util.TimeUtils
import util.syncGetHtml
import util.syncGetJson
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

class Spider {

    private val JSESSIONID = System.getenv("JSESSIONID") ?: ""

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
            .addHeader("Cookie", "JSESSIONID=$JSESSIONID")
            .build()

        chain.proceed(modifiedRequest)
    }

    private val client: OkHttpClient = OkHttpUtils.newBuilder(true)
        .addInterceptor(headerInterceptor)
        .proxy(proxy)
        .build()

    fun checkAlive(): Boolean {
        val html = client.syncGetHtml(
            "https://api.issks.com//issksapi/V2/ec/userInfo.shtml"
        )
        return "请在微信客户端打开链接" !in html
    }

    fun fetchOutletList(station: Station): List<Outlet> {
        val json = client.syncGetJson(
            "https://api.issks.com/issksapi/V2/ec/chargingList.shtml?stationId=${station.id}"
        )
        return (json.get("list") as ArrayNode).map {
            Outlet(
                it.get("vOutletNo").asText(),
                it.get("vOutletName").asText(),
                it.get("status").asInt(),
                station,
            )
        }
    }

    fun fetchOutletDetail(outlet: Outlet): ChargingStation {
        val html = client.syncGetHtml(
            "https://api.issks.com/issksapi/V2/ec/charging/${outlet.no}.shtml"
        )
        val builder = ChargingStation.Builder()
            .stationName(outlet.station.name)
            .outletName(outlet.name)
            .area(outlet.station.area)
        if ("设备维护中" in html || outlet.status == 3) { // status=3 时页面提示该插座安全隐患不可用
            builder.status(ChargingStation.Status.UNAVAILABLE)
        } else {
            fun String.extractDigit(): Int = try {
                this.filter { it.isDigit() }.toInt()
            } catch (ignored: Exception) {
                0
            }
            Jsoup.parse(html).selectFirst(".charging_state")?.let {
                builder.status(ChargingStation.Status.USING)
                builder.power(it.selectFirst(".state_item:nth-child(1) p")!!.text().extractDigit())
                builder.usedMinutes(it.selectFirst(".state_item:nth-child(2) p")!!.text().extractDigit())
                val totalMinutes = 60 * it.select(".state_item:nth-child(1) span").last()!!
                    .text().extractDigit()
                builder.totalMinutes(if (totalMinutes != 0) totalMinutes else 600)
            }
        }
        return builder.build()
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
