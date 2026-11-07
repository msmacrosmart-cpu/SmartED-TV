// Stubs mínimos das anotações Room usadas fora do processador.
@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.room

@Target(AnnotationTarget.CLASS) annotation class Dao
@Target(AnnotationTarget.CLASS)
annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = [],
    val inheritSuperIndices: Boolean = false,
)

object OnConflictStrategy {
    const val REPLACE = 1
    const val ROLLBACK = 2
    const val ABORT = 3
    const val FAIL = 4
    const val IGNORE = 5
}
@Target(AnnotationTarget.CLASS)
annotation class Index(vararg val value: String, val name: String = "", val unique: Boolean = false)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD) annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.FUNCTION) annotation class Query(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)
@Target(AnnotationTarget.FUNCTION) annotation class Upsert
@Target(AnnotationTarget.FUNCTION) annotation class Delete
@Target(AnnotationTarget.FUNCTION) annotation class Update
@Target(AnnotationTarget.CLASS)
annotation class Database(
    val entities: Array<kotlin.reflect.KClass<*>> = [],
    val version: Int = 1,
    val exportSchema: Boolean = true,
)
abstract class RoomDatabase {
    companion object
}

object Room {
    fun databaseBuilder(context: android.content.Context, klass: Class<*>, name: String?): Builder = Builder()

    class Builder {
        fun fallbackToDestructiveMigration(): Builder = this
        fun <T : RoomDatabase> build(): T = throw UnsupportedOperationException("stub")
    }
}
