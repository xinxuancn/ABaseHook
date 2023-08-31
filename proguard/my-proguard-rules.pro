# https://blog.csdn.net/weixin_42602900/article/details/127671586
# ----------------------------------------------------------------------------
# 混淆的压缩比例，0-7
-optimizationpasses 5
# 指定不去忽略非公共的库的类的成员
-dontskipnonpubliclibraryclassmembers
# 指定混淆是采用的算法
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
#############################################
#            项目中特殊处理部分               #
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage
#############################################
#             丧心病狂的混淆                #
# 指定外部模糊字典 proguard-chinese.txt 改为混淆文件名，下同
-obfuscationdictionary proguard-sxbk.txt
# 指定class模糊字典
-classobfuscationdictionary proguard-sxbk.txt
# 指定package模糊字典
-packageobfuscationdictionary proguard-sxbk.txt
#############################################