// Stubs somente para typecheck fora do Android (tools/verify-logic.sh).
@file:Suppress("unused", "UNUSED_PARAMETER")

package android.content

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getFloat(key: String, defValue: Float): Float
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun apply()
    }

    companion object { const val MODE_PRIVATE = 0 }
}

open class Context {
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        throw UnsupportedOperationException("stub")
    open val applicationContext: Context get() = this

    companion object { const val MODE_PRIVATE = 0 }
}
