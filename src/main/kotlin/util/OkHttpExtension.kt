package util

import com.fasterxml.jackson.databind.JsonNode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.io.IOException

fun OkHttpClient.syncGetHtml(url: String): String {
    val request = Request.Builder()
        .url(url)
        .build()
    val maxTries = 3
    for (i in 1..maxTries) {
        try {
            val resp = this.newCall(request).execute()
            if (!resp.isSuccessful) {
                throw IOException("Unexpected code $resp")
            }
            val result = resp.body!!.string()
            resp.closeQuietly()
            return result
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
            val resp = this.newCall(request).execute()
            if (!resp.isSuccessful) {
                throw IOException("Unexpected code $resp")
            }
            val result = JsonUtils.toJson(resp.body!!.string())!!
            resp.closeQuietly()
            return result
        } catch (e: Exception) {
            if (i == maxTries) {
                throw e
            }
        }
    }
    throw RuntimeException("unreachable")
}
