# Dropi release build (R8).
# Libraries (Compose, WorkManager) ship their own rules; manifest components are kept by AAPT.

# Readable stack traces in the on-device crash report.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enum names are saved in the database and prefs (meal tags, units): keep the constants' names
# and the values()/valueOf() lookups, but let R8 optimise the rest of each enum.
-keepclassmembers,allowoptimization enum app.dropi.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# LiteRT-LM: its native code looks up these classes and members by name (callbacks, configs,
# input data), and it reads its own JSON with Gson.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
