// Stubs fiéis de OkHttp, Okio e kotlinx-serialization, usados APENAS para
// typecheck do núcleo fora do Android (tools/verify-logic.sh).
// As assinaturas reproduzem a API real para que erros de uso apareçam.
@file:Suppress("unused", "UNUSED_PARAMETER")

package okhttp3

import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class MediaType(val mediaType: String)

abstract class RequestBody
abstract class ResponseBody : Closeable {
    abstract fun string(): String?
    abstract fun bytes(): ByteArray?
    override fun close() {}
}

class Headers {
    fun get(name: String): String? = null
}

class Request private constructor(
    @JvmField val url: HttpUrl,
    @JvmField val method: String,
    @JvmField val headers: Headers,
    @JvmField val body: RequestBody?,
) {
    fun newBuilder(): Builder = Builder(this)

    class Builder {
        constructor()
        constructor(request: Request)
        fun url(url: String): Builder = this
        fun url(url: HttpUrl): Builder = this
        fun header(name: String, value: String): Builder = this
        fun addHeader(name: String, value: String): Builder = this
        fun get(): Builder = this
        fun head(): Builder = this
        fun post(body: RequestBody): Builder = this
        fun build(): Request = throw UnsupportedOperationException("stub")
    }
}

class HttpUrl {
    fun newBuilder(): Builder = Builder()
    class Builder {
        fun build(): HttpUrl = throw UnsupportedOperationException("stub")
    }
}

class Response : Closeable {
    val isSuccessful: Boolean get() = true
    val code: Int get() = 200
    val body: ResponseBody? get() = null
    fun header(name: String): String? = null
    fun headers(): Headers = Headers()
    override fun close() {}
}

class Call {
    fun execute(): Response = throw UnsupportedOperationException("stub")
    fun cancel() {}
    fun enqueue(callback: Callback) {}
}

fun interface Callback {
    fun onFailure(call: Call, e: java.io.IOException)
    fun onResponse(call: Call, response: Response) {}
}

class Dispatcher {
    val executorService: ExecutorService get() = throw UnsupportedOperationException("stub")
}

class ConnectionPool {
    fun evictAll() {}
}

class Cache(private val directory: File?, private val maxSize: Long) : Closeable {
    override fun close() {}
}

class OkHttpClient private constructor() {
    val dispatcher: Dispatcher get() = Dispatcher()
    val connectionPool: ConnectionPool get() = ConnectionPool()
    fun newCall(request: Request): Call = Call()
    fun newBuilder(): Builder = Builder()
    fun shutdown() {}

    class Builder {
        fun cache(cache: Cache?): Builder = this
        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun callTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun retryOnConnectionFailure(retry: Boolean): Builder = this
        fun followRedirects(follow: Boolean): Builder = this
        fun followSslRedirects(follow: Boolean): Builder = this
        fun addInterceptor(interceptor: Interceptor): Builder = this
        fun addNetworkInterceptor(interceptor: Interceptor): Builder = this
        fun build(): OkHttpClient = throw UnsupportedOperationException("stub")
    }
}

fun interface Interceptor {
    fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
    }
}


