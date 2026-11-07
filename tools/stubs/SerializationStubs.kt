// Stubs de kotlinx.serialization: apenas o suficiente para typecheck.
@file:Suppress("unused", "UNUSED_PARAMETER")
package kotlinx.serialization

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Serializable(val with: kotlin.reflect.KClass<*> = Unit::class)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
annotation class SerialName(val value: String)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
annotation class Transient

abstract class KSerializer<T>
