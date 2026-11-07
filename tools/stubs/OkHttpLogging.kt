@file:Suppress("unused", "UNUSED_PARAMETER")
package okhttp3.logging

class HttpLoggingInterceptor : okhttp3.Interceptor {
    constructor()
    constructor(level: Level)
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response =
        throw UnsupportedOperationException("stub")

    enum class Level { NONE, BASIC, HEADERS, BODY }
    fun setLevel(level: Level): HttpLoggingInterceptor = this
}
