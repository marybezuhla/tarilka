# R8 shrinks and optimises the release build. Kept here: only what is reached from native code or by reflection.

# SQLCipher: its native library calls back into these classes by name
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# LiteRT-LM (GitHub build only): the on-phone AI runtime is native and calls back into its Java/Kotlin classes
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# The PDF request crosses rotation as Java Serializable: keep its fields and serial form
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
    <fields>;
}

# No logging reaches a release build (the app has none; this keeps it so)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Shrink and optimise, but keep the names: the code is open anyway, and a crash report the person sends
# must be readable without a mapping file
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
