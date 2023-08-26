package cc.abase.lsposed.hook

import android.app.Application
import android.content.Context
import cc.abase.lsposed.utils.MyUtils
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.TimeUnit


/**
 * 原文地址：https://blog.csdn.net/moziqi123/article/details/109204801
 * Hook打印非加密的Okhttp请求记录
 * Author:Khaos
 * Date:2023/8/23
 * Time:17:43
 */
class HookOkhttpLog(private val lpparam: XC_LoadPackage.LoadPackageParam) {
  //<editor-fold defaultstate="collapsed" desc="监听Application启动，主要是在这里面判断进程，一般需要主进程中进行Hook">
  init {
    //Hook对象Application，Hook方法Hook对象Application.attach(Context)
    XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
      override fun afterHookedMethod(param: MethodHookParam) {
        val context = param.thisObject as Context
        val pa = MyUtils.currentProcessPackage(context)
        if (pa != "cc.abase.lsposed" && !pa.contains(":")) hookLog(context, pa)//防止有推送等进程，比如-->com.abase.s1:pushcore
      }
    })
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Hook打印请求日志">
  private fun hookLog(context: Context, pa: String) {
    XposedBridge.log("Hook OkHttp进程包名:$pa")
    //------------------------------------判断是否使用了OkHttp------------------------------------//
    val clsOkHttp = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient", lpparam.classLoader)
    if (clsOkHttp == null) {
      XposedBridge.log("Hook OkHttp失败，没有使用Okhttp或者被混淆了")
      return
    }
    //------------------------------------读取OkHttp使用的版本------------------------------------//
    val clsOkVersionOld = XposedHelpers.findClassIfExists("okhttp3.internal.Version", lpparam.classLoader)
    val clsOkVersionNew = XposedHelpers.findClassIfExists("okhttp3.OkHttp", lpparam.classLoader)
    var versionOkhttp = ""
    if (clsOkVersionOld != null) {
      versionOkhttp = XposedHelpers.callMethod(clsOkVersionOld, "userAgent")?.toString()?.replace("okhttp/", "")?.trim() ?: ""
    } else if (clsOkVersionNew != null) {
      versionOkhttp = XposedHelpers.getStaticObjectField(clsOkVersionNew, "VERSION")?.toString()?.trim() ?: ""
    }
    if (versionOkhttp.isBlank()) {
      XposedBridge.log("Hook OkHttp版本号获取失败")
      return
    }
    XposedBridge.log("Hook OkHttp版本号:$versionOkhttp")
    //------------------------------------初始化自己需添加的拦截器------------------------------------//
    val clsCacheInterceptor = XposedHelpers.findClassIfExists("okhttp3.internal.cache.CacheInterceptor", lpparam.classLoader)//okhttp3.internal.cache.CacheInterceptor
    val clsCacheOld = XposedHelpers.findClassIfExists("okhttp3.internal.cache.InternalCache", lpparam.classLoader)
    val clsCacheNew = XposedHelpers.findClassIfExists("okhttp3.Cache", lpparam.classLoader)
    var logInterceptor: Any? = null//日志拦截器
    if (clsCacheOld != null) {//构建旧版本的okhttp3.internal.cache.CacheInterceptor
      logInterceptor = XposedHelpers.newInstance(clsCacheInterceptor, mutableListOf(clsCacheOld).toTypedArray(), null)
    } else if (clsCacheNew != null) {//构建新版本的okhttp3.internal.cache.CacheInterceptor
      logInterceptor = XposedHelpers.newInstance(clsCacheInterceptor, mutableListOf(clsCacheNew).toTypedArray(), null)
    }
    if (logInterceptor == null) {
      XposedBridge.log("Hook OkHttp创建Log拦截失败")
      return
    }
    val clsChain = XposedHelpers.findClass("okhttp3.Interceptor.Chain", lpparam.classLoader)//参数类型
    //Hook日志拦截
    XposedHelpers.findAndHookMethod(logInterceptor.javaClass, "intercept", clsChain, object : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam) {
        super.beforeHookedMethod(param)
        val cache = XposedHelpers.getObjectField(param.thisObject, "cache")//判断cache对象是否为空，如果为空则表示是我们自己设置的，毕竟真实项目中不太可能设置空cache
        val request = XposedHelpers.callMethod(param.args.first(), "request")//Chain.request()
        val tags = XposedHelpers.getObjectField(request, "tags") as Map<Class<*>, Any>
        val printStr = tags[java.lang.String::class.java]?.toString() ?: ""
        if (cache == null && printStr != "noPrint") printRequestInfo(param)//处理之前打印
      }

      override fun afterHookedMethod(param: MethodHookParam) {
        super.afterHookedMethod(param)
        val cache = XposedHelpers.getObjectField(param.thisObject, "cache")//判断cache对象是否为空，如果为空则表示是我们自己设置的，毕竟真实项目中不太可能设置空cache
        val request = XposedHelpers.callMethod(param.args.first(), "request")//Chain.request()
        val tags = XposedHelpers.getObjectField(request, "tags") as Map<Class<*>, Any>
        val noPrint = tags[java.lang.String::class.java]?.toString() ?: ""
        if (cache == null && noPrint != "noPrint") printResponseInfo(param)
        val response = param.result
        if (response != null) {//防止报错
          XposedHelpers.setObjectField(response, "networkResponse", null)
          XposedHelpers.setObjectField(response, "cacheResponse", null)
          XposedHelpers.setObjectField(response, "priorResponse", null)
        }
      }
    })
    //------------------------------------监听OkHttp构造------------------------------------//
    val clsOkClientBuilder = XposedHelpers.findClass("okhttp3.OkHttpClient\$Builder", lpparam.classLoader)
    //Request.newBuilder,需要设置不打印
    val clsRequest = XposedHelpers.findClass("okhttp3.Request", lpparam.classLoader)
    XposedHelpers.findAndHookMethod(clsRequest, "newBuilder", object : XC_MethodHook() {
      override fun afterHookedMethod(param: MethodHookParam) {
        super.afterHookedMethod(param)
        //新请求Request.Builder不再重复打印 TODO XX 其实这里是需要判断是否使用了新的请求，如果使用了，则打印新的，否则打印旧的
        val requestBuilder = param.result//Request.Builder
        val tagsNew = XposedHelpers.getObjectField(requestBuilder, "tags") as Map<Class<*>, Any>
        val newMaps: MutableMap<Class<*>, Any> = mutableMapOf()
        tagsNew.entries.forEach { m -> newMaps[m.key] = m.value }
        newMaps[java.lang.String::class.java] = java.lang.String.valueOf("noPrint")//是否需要打印
        XposedHelpers.setObjectField(requestBuilder, "tags", newMaps)
      }
    })
    //Response.newBuilder,需要设置不打印
    val clsRequestResponse = XposedHelpers.findClass("okhttp3.Response", lpparam.classLoader)
    XposedHelpers.findAndHookMethod(clsRequestResponse, "newBuilder", object : XC_MethodHook() {
      override fun afterHookedMethod(param: MethodHookParam) {
        super.afterHookedMethod(param)
        //新响应Response.Builder不再重复打印 TODO XX 其实这里是需要判断是否使用了新的结果，如果使用了，则打印新的，否则打印旧的
        //val response = param.thisObject//okhttp3.Response
        //val request = XposedHelpers.callMethod(response, "request")//Response.request()
        //val tagsNew = XposedHelpers.getObjectField(request, "tags") as Map<Class<*>, Any>
        //val newMaps: MutableMap<Class<*>, Any> = mutableMapOf()
        //tagsNew.entries.forEach { m -> newMaps[m.key] = m.value }
        //newMaps[java.lang.String::class.java] = java.lang.String.valueOf("noPrint")//是否需要打印
        //XposedHelpers.setObjectField(request, "tags", newMaps)
      }
    })
    //监听OkHttp构造
    XposedHelpers.findAndHookConstructor(clsOkHttp, clsOkClientBuilder, object : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam) {
        super.beforeHookedMethod(param)
        val okHttpClientBuilder = param.args.first()//OkHttpClient.Builder
        val interceptors = XposedHelpers.callMethod(okHttpClientBuilder, "interceptors") as MutableList<Any>//OkHttpClient.Builder.interceptors()
        //有拦截器的不再添加
        for (any in interceptors) {
          if (clsCacheInterceptor != null && any.javaClass == clsCacheInterceptor) {//如果是自己添加的拦截器类型
            val cache = XposedHelpers.getObjectField(any, "cache")//判断cache对象是否为空，如果为空则表示是我们自己设置的，毕竟真实项目中不太可能设置空cache
            if (cache == null) {
              XposedBridge.log("Hook OkHttp已经添加了拦截器的，不再重复添加")
              return
            }
          }
        }
        //------------------添加拦截器【Request：先添加先执行；Response：先添加后执行】------------------//
        if (interceptors.size <= 1) {
          interceptors.add(logInterceptor)//拦截器数量小于2，默认没有加解密，直接添加
        } else {//2个以及以上，简单判断一下是否加解密
          val hasCode = interceptors.map { a -> a.javaClass.simpleName.lowercase() }.any { a -> a.contains("enc") || a.contains("dec") }
          if (hasCode) {
            interceptors.add(interceptors.size - 2, logInterceptor)
          } else {
            interceptors.add(logInterceptor)
          }
        }
        XposedBridge.log("OkHttpClient.newBuilder添加新的拦截器")
      }
    })
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="打印请求信息">
  private fun printRequestInfo(param: XC_MethodHook.MethodHookParam) {
    val request = XposedHelpers.callMethod(param.args.first(), "request")//Chain.request()
    val url = XposedHelpers.getObjectField(request, "url")//读取对象 Request.url
    if (!(url?.toString() ?: "").lowercase().startsWith("http")) {
      XposedBridge.log(Throwable("Hook OkHttp请求地址异常:$url"))
      return
    }
    val isMedia = MyUtils.isMediaType(url?.toString() ?: "")
    if (isMedia) {//媒体类不打印
      XposedBridge.log(Throwable("Hook OkHttp媒体类不打印:$url"))
      return
    }
    val method = XposedHelpers.getObjectField(request, "method")//读取对象 Request.method
    val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
    val requestBody = XposedHelpers.getObjectField(request, "body")//读取对象 Request.body
    //请求参数
    val sb = StringBuilder()
    sb.append(">>>>>>>>Okhttp.Request.START>>>----------------------------------->")
      .append("\n请求接口:  ").append(method).append("  ").append(url)
      .append("\n\n请求头:\n").append(requestHeaders.toString().trim())
    var requestParams = ""
    requestBody?.let { bo ->
      requestParams = requestBody2String(bo)
      val bodyStr = MyUtils.unescapeJson("请求参数", requestParams)
      sb.append("\n\n请求体:\n")
        .append(if (requestParams.isBlank()) "有请求Body,可能被混淆了，无法读取" else MyUtils.jsonFormat(bodyStr))
    }
    sb.append("\n<<<<<<<<Okhttp.Request.END<<<-------------------------------------")
    //打印
    XposedBridge.log("Hook 简单打印 OkHttp 接口请求: $method $url")//单独打印接口请求信息
    XposedBridge.log(sb.toString())
    //设置请求时间
    val tags = XposedHelpers.getObjectField(request, "tags") as Map<Class<*>, Any>
    val newMaps: MutableMap<Class<*>, Any> = mutableMapOf()
    tags.entries.forEach { m -> newMaps[m.key] = m.value }
    newMaps[java.lang.Long::class.java] = java.lang.Long.valueOf(System.nanoTime())
    XposedHelpers.setObjectField(request, "tags", newMaps)
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="打印响应信息">
  private fun printResponseInfo(param: XC_MethodHook.MethodHookParam) {
    val request = XposedHelpers.callMethod(param.args.first(), "request")//Chain.request()
    val url = XposedHelpers.getObjectField(request, "url")//读取对象 Request.url
    val method = XposedHelpers.getObjectField(request, "method")//读取对象 Request.method
    val startTime = XposedHelpers.callMethod(request, "tag", java.lang.Long::class.java)?.toString()?.toLong() ?: 0L
    val currentTime = System.nanoTime()
    val tookMs = TimeUnit.NANOSECONDS.toMillis(currentTime - startTime)
    if (param.throwable != null) {//响应失败
      XposedBridge.log("Hook 简单打印 请求失败：$method ${tookMs}ms $url ${param.throwable.message}")//单独打印接口请求信息
    } else {//响应成功
      val isMedia = MyUtils.isMediaType(url?.toString() ?: "")
      if (isMedia) {//媒体类不打印
        XposedBridge.log(Throwable("Hook OkHttp媒体类不打印:$url"))
        return
      }
      //出参okhttp3.Response
      param.result?.let { response ->
        val t1 = XposedHelpers.getObjectField(response, "sentRequestAtMillis")?.toString()?.toLong() ?: 0
        val t2 = XposedHelpers.getObjectField(response, "receivedResponseAtMillis")?.toString()?.toLong() ?: 0
        XposedBridge.log("Hook OkHttp 耗时:${t2 - t1}ms $url")
        //请求结果
        val responseHeaders = XposedHelpers.getObjectField(response, "headers").toString().trim()//读取对象 Response.headers
        val sb = StringBuilder()
        sb.append(">>>>>>>>Okhttp.Response.START>>>----------------------------------->")
        //XposedBridge.log("Hook 简单打印 OkHttp 接口响应: $method ${tookMs}ms $url")//单独打印接口响应时长
        sb.append("\n响应接口:  ").append(method).append("  ").append(tookMs).append("ms").append("  ").append(url)
          .append("\n\n响应头:\n").append(responseHeaders)
        val responseBody = XposedHelpers.getObjectField(response, "body")//Response.ResponseBody
        responseBody?.let { bo -> sb.append("\n\n响应体:\n").append(responseBody2String(responseHeaders, bo)) }
        sb.append("\n<<<<<<<<Okhttp.Response.END<<<-------------------------------------")
        //打印
        XposedBridge.log(sb.toString())
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Request.body的String【未格式化】">
  private fun requestBody2String(requestBody: Any): String {
    val classBuffer = XposedHelpers.findClassIfExists("okio.Buffer", lpparam.classLoader)//判断是否存在okio.Buffer，如果被混淆则无法获取到，则需要修改新路径
    return if (classBuffer != null) {
      val obBuffer = classBuffer.newInstance()//先把数据写入Buffer，再从先把数据写入Buffer读取
      XposedHelpers.callMethod(requestBody, "writeTo", obBuffer)//写入：RequestBody.writeTo(okio.Buffer())
      XposedHelpers.callMethod(obBuffer, "readString", Charsets.UTF_8)?.toString() ?: ""//读取：okio.Buffer.readString(Charsets.UTF_8)
    } else {
      ""
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Response.body的String【已格式化】">
  private fun responseBody2String(responseHeaders: String, responseBody: Any): String {
    val mediaType = XposedHelpers.callMethod(responseBody, "contentType")//ResponseBody.contentType()
    return if (mediaType != null) {
      val subtype = XposedHelpers.getObjectField(mediaType, "subtype")//MediaType.subtype
      if (MyUtils.isCanParsable(subtype?.toString())) {//通过响应类型判断，可以解析的才解析
        //响应头处理
        val headerMaps = mutableMapOf<String, String>()
        for (s in responseHeaders.split("\n")) {
          if (s.contains(":")) {
            val split = s.split(":")
            headerMaps[split[0]] = split[1]
          }
        }
        //数据是否压缩
        val isGzip = headerMaps["Content-Encoding"]?.lowercase() == "gzip"
        val bufferedSource = XposedHelpers.callMethod(responseBody, "source")//ResponseBody.source()
        XposedHelpers.callMethod(bufferedSource, "request", Long.MAX_VALUE)//BufferedSource.request(Long)
        val buffer = XposedHelpers.callMethod(bufferedSource, "buffer") //BufferedSource.buffer()
        val newBuffer = XposedHelpers.callMethod(buffer, "clone") //Buffer.clone()
        val result = XposedHelpers.callMethod(newBuffer, "readString", Charsets.UTF_8) //Buffer.readString(Charsets.UTF_8)
        if (result != null) {
          val bodyStr = MyUtils.unescapeJson("响应参数", result.toString())
          MyUtils.jsonFormat(if (isGzip) MyUtils.decompressGzipString(bodyStr) else bodyStr)
        } else {
          "有响应Body,可能被混淆了，无法读取"
        }
      } else {
        "不解析该类型的响应体:${mediaType}"
      }
    } else {
      "没有contentType，不解析响应体"
    }
  }
  //</editor-fold>
}