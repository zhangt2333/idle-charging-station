package util

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager


object OkHttpUtils {

    // Create a trust manager that does not validate certificate chains
    private val trustManager = object : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate?>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    // Install the all-trusting trust manager
    private val sslContext = SSLContext.getInstance("SSL").also {
        it.init(null, arrayOf(trustManager), SecureRandom())
    }

    @Throws(RuntimeException::class)
    fun newBuilder(ignoreCertificate: Boolean = false): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()

        if (ignoreCertificate) {
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder
    }

    @Throws(RuntimeException::class)
    fun newClient(ignoreCertificate: Boolean = false): OkHttpClient {
        return newBuilder(ignoreCertificate).build()
    }
}
