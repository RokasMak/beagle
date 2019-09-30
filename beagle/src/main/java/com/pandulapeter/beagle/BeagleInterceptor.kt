package com.pandulapeter.beagle

import com.pandulapeter.beagle.models.NetworkLogItem
import com.pandulapeter.beagleCore.contracts.BeagleInterceptorContract
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.EOFException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

//TODO: Set maximum size for Strings to avoid crashes when parsing huge JSON-s.
object BeagleInterceptor : BeagleInterceptorContract {

    private val UTF8 = Charset.forName("UTF-8")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body
        val requestJson = if (requestBody == null) {
            ""
        } else if (bodyHasUnknownEncoding(request.headers)) {
            "[encoded]"
        } else {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            var charset: Charset? = UTF8
            val contentType = requestBody.contentType()
            if (contentType != null) {
                charset = contentType.charset(UTF8)
            }
            if (isPlaintext(buffer)) {
                charset?.let { buffer.readString(it) } ?: ""
            } else {
                "[Binary ${requestBody.contentLength()} -byte body]"
            }
        }
        //TODO: Request headers are not properly parsed
        Beagle.logNetworkEvent(
            NetworkLogItem(
                isOutgoing = true,
                body = requestJson.formatToJson() ?: "",
                headers = request.headers.map { "[${it.first}]: ${it.second}" },
                url = "[${request.method}] ${request.url}"
            )
        )
        val startNs = System.nanoTime()
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (exception: Exception) {
            Beagle.logNetworkEvent(
                NetworkLogItem(
                    isOutgoing = false,
                    body = exception.message ?: "HTTP Failed",
                    url = "FAIL [${request.method}] ${request.url}",
                    duration = -1L
                )
            )
            throw exception
        }
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val responseBody = response.body
        val responseJson = response.body?.string()
        Beagle.logNetworkEvent(
            NetworkLogItem(
                isOutgoing = false,
                body = responseJson?.formatToJson() ?: response.message,
                headers = response.headers.map { "[${it.first}]: ${it.second}" },
                url = "${response.code} [${request.method}] ${request.url}",
                duration = tookMs
            )
        )
        return response.newBuilder().body(responseJson?.toResponseBody(responseBody?.contentType())).build()
    }

    private fun String?.formatToJson() = try {
        val obj = JSONTokener(this).nextValue()
        if (obj is JSONObject) obj.toString(4) else (obj as? JSONArray)?.toString(4) ?: (obj as String)
    } catch (e: JSONException) {
        null
    }

    private fun isPlaintext(buffer: Buffer): Boolean {
        try {
            val prefix = Buffer()
            val byteCount = if (buffer.size < 64) buffer.size else 64
            buffer.copyTo(prefix, 0, byteCount)
            for (i in 0..15) {
                if (prefix.exhausted()) {
                    break
                }
                val codePoint = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false
                }
            }
            return true
        } catch (e: EOFException) {
            return false
        }
    }

    private fun bodyHasUnknownEncoding(headers: Headers) = headers["Content-Encoding"].let { contentEncoding ->
        (contentEncoding != null
                && !contentEncoding.equals("identity", ignoreCase = true)
                && !contentEncoding.equals("gzip", ignoreCase = true))
    }
}