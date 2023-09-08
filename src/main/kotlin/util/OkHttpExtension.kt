package util

import com.fasterxml.jackson.databind.JsonNode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException

fun OkHttpClient.syncGetHtml(url: String): String {
    val request = Request.Builder()
        .url(url)
        .build()
    val maxTries = 3
    for (i in 1..maxTries) {
        try {
            this.newCall(request).execute().use {
                if (!it.isSuccessful) {
                    throw IOException("Unexpected code $it")
                }
                return@syncGetHtml it.body!!.string()
            }
        } catch (e: Exception) {
            if (i == maxTries) {
                throw e
            }
        }
    }
    throw RuntimeException("unreachable")
}

fun OkHttpClient.syncGetJson(url: String): JsonNode {
    val request = Request.Builder()
        .url(url)
        .build()
    val maxTries = 3
    for (i in 1..maxTries) {
        try {
            this.newCall(request).execute().use {
                if (!it.isSuccessful) {
                    throw IOException("Unexpected code $it")
                }
                return@syncGetJson JsonUtils.toJson(it.body!!.string())!!
            }
        } catch (e: Exception) {
            if (i == maxTries) {
                throw e
            }
        }
    }
    throw RuntimeException("unreachable")
}
