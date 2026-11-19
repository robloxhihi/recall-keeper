# v1: R8 dang tat. Giu luat toi thieu cho Room + Compose de bat minify sau nay khong vo.
-keep class com.robloxhihi.recallkeeper.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn org.jetbrains.annotations.**
