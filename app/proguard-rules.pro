-keep class ir.shahed.pahpad.MainActivity { *; }
-dontwarn kotlin.**

# ---- Tapsell Plus (تبلیغات) ----
-keep class ir.tapsell.** { *; }
-dontwarn ir.tapsell.**
-dontwarn org.jetbrains.annotations.**

# حفظ شماره خط برای گزارش کرش داخلی
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
