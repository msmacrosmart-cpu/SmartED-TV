// Stub de androidx.room.migration.Migration (typecheck sem Android SDK).
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.room.migration

import androidx.sqlite.db.SupportSQLiteDatabase

abstract class Migration(val startVersion: Int, val endVersion: Int) {
    abstract fun migrate(db: SupportSQLiteDatabase)
}
