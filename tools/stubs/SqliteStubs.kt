// Stubs mínimos de androidx.sqlite e androidx.room.migration para o typecheck
// fora do Android (tools/verify-logic.sh).
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.sqlite.db

interface SupportSQLiteDatabase {
    fun execSQL(sql: String)
    fun query(query: String): SupportSQLiteCursor
}

interface SupportSQLiteCursor {
    fun moveToFirst(): Boolean
    fun getString(index: Int): String?
    fun close()
}
