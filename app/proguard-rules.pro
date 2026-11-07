# kotlinx.serialization uses generated serializers; the plugin supplies consumer rules.
# Keep application constructors used by the Android framework.
-keep class com.smarted.tv.SmartEdApplication { *; }
-keep class com.smarted.tv.MainActivity { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
