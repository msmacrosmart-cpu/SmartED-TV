# kotlinx.serialization uses generated serializers; the plugin supplies consumer rules.
# Keep application constructors used by the Android framework.
-keep class com.smarted.tv.SmartEdApplication { *; }
-keep class com.smarted.tv.MainActivity { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# --- Retrofit 2.11 (árvore OnePlay: HTTP nativo leve; regras oficiais p/ R8 full mode) ---
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
