@file:Suppress("unused", "UNUSED_PARAMETER")
package android.content.res

abstract class AssetManager {
    abstract fun open(fileName: String): java.io.InputStream
}
