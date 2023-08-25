# https://blog.csdn.net/weixin_42602900/article/details/127671586
# ----------------------------------------------------------------------------
# 混淆的压缩比例，0-7
-optimizationpasses 5
# 指定不去忽略非公共的库的类的成员
-dontskipnonpubliclibraryclassmembers
# 指定混淆是采用的算法
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
#
# ----------------------------- 第三方库、框架、SDK -----------------------------
## Gson
#-keep class com.google.gson.stream.** { *; }
#-keepattributes EnclosingMethod
#-dontwarn com.google.gson.**
#-keep class com.google.gson.**{*;}
#-keep interface com.google.gson.**{*;}
##gson
##如果用到Gson解析包的，直接添加下面这几行就能成功混淆，不然会报错。
###---------------Begin: proguard configuration for Gson  ----------
## Gson uses generic type information stored in a class file when working with fields. Proguard
## removes such information by default, so configure it to keep all of it.
#-keepattributes Signature
## For using GSON @Expose annotation
#-keepattributes *Annotation*
## Gson specific classes
#-dontwarn sun.misc.**
##-keep class com.google.gson.stream.** { *; }
## Prevent proguard from stripping interface information from TypeAdapterFactory,
## JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
#-keep class * implements com.google.gson.TypeAdapterFactory
#-keep class * implements com.google.gson.JsonSerializer
#-keep class * implements com.google.gson.JsonDeserializer
# Application classes that will be serialized/deserialized over Gso
#############################################
#            项目中特殊处理部分               #
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage
#-keep class cc.abase.lsposed.hook.** {*;}
#############################################
#             丧心病狂的混淆                #
# 指定外部模糊字典 proguard-chinese.txt 改为混淆文件名，下同
-obfuscationdictionary proguard-sxbk.txt
# 指定class模糊字典
-classobfuscationdictionary proguard-sxbk.txt
# 指定package模糊字典
-packageobfuscationdictionary proguard-sxbk.txt
#############################################