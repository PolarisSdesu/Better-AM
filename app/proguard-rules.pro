# libxposed instantiates the entry point by name from
# META-INF/xposed/java_init.list, so it has to survive shrinking and renaming.
-keep class moe.polariss.betteram.hook.BetterAmModule { *; }

# The log provider is named from the manifest and is called across processes by
# the module's own activity, so keep it intact too.
-keep class moe.polariss.betteram.log.LogProvider { *; }
-keep class moe.polariss.betteram.log.LogBridge { *; }
-keep class moe.polariss.betteram.log.LogStore { *; }

# The module only references the API it runs against; nothing is reflected into.
-dontwarn io.github.libxposed.**
