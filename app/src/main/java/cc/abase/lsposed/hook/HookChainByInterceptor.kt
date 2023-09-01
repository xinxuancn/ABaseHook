package cc.abase.lsposed.hook

import android.app.Application
import android.content.Context
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.config.AppConfig
import cc.abase.lsposed.receiver.MyBroadcastReceiver
import cc.abase.lsposed.utils.MyUtils
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 这里用到了不同进程同步消息，使用的是广播模式，否则2个APP没办法进行数据同步
 * 原文地址：https://blog.csdn.net/moziqi123/article/details/109204801
 * Author:Khaos
 * Date:2023/8/28
 * Time:16:34
 */
class HookChainByInterceptor(private val lpparam: XC_LoadPackage.LoadPackageParam) {
  //<editor-fold defaultstate="collapsed" desc="变量">
  private val TAG = "HookChainByInterceptor"

  //Request.newBuilder时将就请求设置为不打印
  private val headerTagPrint = "hookPrint"

  //通过拦截器请求时，在请求头添加当前拦截器名称
  private val headerTagInterceptor = "hookInterceptor"

  //Okhttp发起请求前，在请求头添加所有拦截器名称
  private val headerTagInterceptors = "hookInterceptors"
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="监听Application启动，主要是在这里面判断进程，一般需要主进程中进行Hook">
  init {
    //Hook对象Application，Hook方法Hook对象Application.attach(Context)
    XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
      override fun afterHookedMethod(param: MethodHookParam) {
        val context = param.thisObject as Context
        val pa = MyUtils.currentProcessPackage(context)
        if (pa != AppConfig.myPackageName && !pa.contains(":")) hookChain(context, pa)//防止有推送等进程，比如-->com.abase.s1:pushcore
        else if (pa == AppConfig.myPackageName) {
          //这里可以执行测试代码
        }
      }
    })
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Hook打印请求日志">
  private fun hookChain(context: Context, pa: String) {
    //<editor-fold defaultstate="collapsed" desc="判断是否使用Okhttp或者被混淆了">
    XposedBridge.log("$TAG 进程包名:$pa")
    val clsRealChain = XposedHelpers.findClassIfExists("okhttp3.internal.http.RealInterceptorChain", lpparam.classLoader)
    if (clsRealChain == null) {
      XposedBridge.log("$TAG Hook失败，没有使用Okhttp或者被混淆了")
      return
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="读取OkHttp使用的版本">
    val clsOkVersionOld = XposedHelpers.findClassIfExists("okhttp3.internal.Version", lpparam.classLoader)
    val clsOkVersionNew = XposedHelpers.findClassIfExists("okhttp3.OkHttp", lpparam.classLoader)
    var versionOkhttp = ""
    if (clsOkVersionOld != null) {
      versionOkhttp = XposedHelpers.callMethod(clsOkVersionOld, "userAgent")?.toString()?.replace("okhttp/", "")?.trim() ?: ""
    } else if (clsOkVersionNew != null) {
      versionOkhttp = XposedHelpers.getStaticObjectField(clsOkVersionNew, "VERSION")?.toString()?.trim() ?: ""
    }
    if (versionOkhttp.isBlank()) {
      XposedBridge.log("$TAG OkHttp版本号获取失败")
      return
    }
    XposedBridge.log("$TAG OkHttp版本号:$versionOkhttp")
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="处理Class<?>类型">
    val clsRequest = XposedHelpers.findClassIfExists("okhttp3.Request", lpparam.classLoader)
    val clsOkhttpClient = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient", lpparam.classLoader)
    val clsChain = XposedHelpers.findClassIfExists("okhttp3.Interceptor.Chain", lpparam.classLoader)
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="OkhttpClient.newCall发送请求前将拦截器名称设置到Header + 监听拦截器发送请求">
    XposedHelpers.findAndHookMethod(clsOkhttpClient, "newCall", clsRequest, object : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam) {
        super.beforeHookedMethod(param)
        val request = param.args.first()
        val okhttpClient = param.thisObject
        val interceptors = XposedHelpers.getObjectField(okhttpClient, "interceptors") as List<Any>
        val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
        val oldBuilder1 = XposedHelpers.callMethod(requestHeaders, "newBuilder") //Headers.Builder
        val interceptorNames = interceptors.joinToString(separator = ",") { a -> a.javaClass.name.toString() }
        val oldBuilder2 = XposedHelpers.callMethod(oldBuilder1, "set", headerTagInterceptors, interceptorNames)
        val newRequestHeaders = XposedHelpers.callMethod(oldBuilder2, "build")
        XposedHelpers.setObjectField(request, "headers", newRequestHeaders)
        hookInterceptor(interceptors.map { a -> a.javaClass }.toMutableList(), clsChain)
      }
    })
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="抓取请求信息">
    //Hook日志拦截
    XposedHelpers.findAndHookMethod(clsRealChain, "proceed", clsRequest, object : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam) {
        super.beforeHookedMethod(param)
        val request = param.args.first()//okhttp3.Request
        val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
        var url = XposedHelpers.getObjectField(request, "url")?.toString() ?: ""//读取对象 Request.url
        url = MyUtils.formatUrl(url)
        val hasPrint = XposedHelpers.callMethod(requestHeaders, "get", headerTagPrint)?.toString() ?: ""//Headers.get(String)
        val tagInterceptor = XposedHelpers.callMethod(requestHeaders, "get", headerTagInterceptor)?.toString() ?: ""//Headers.get(String)
        val tagInterceptors = XposedHelpers.callMethod(requestHeaders, "get", headerTagInterceptors)?.toString() ?: ""//Headers.get(String)
        val tagUpgrade = XposedHelpers.callMethod(requestHeaders, "get", "Upgrade")?.toString() ?: ""//Headers.get(String)
        var needPrint = false
        if (hasPrint != "true" && tagUpgrade.lowercase() != "websocket" && !MyUtils.isMediaType(url)) {//需要打印
          needPrint = if (tagInterceptors.isBlank()) true else tagInterceptor == tagInterceptors.split(",").last()//直接在最后一个拦截器打印
        }
        if (needPrint) {
          printRequestInfo(param)
          //已经打印需要把当前请求设置为已打印，否则会再次发起同样的请求
          val oldBuilder1 = XposedHelpers.callMethod(requestHeaders, "newBuilder") //Headers.Builder
          val oldBuilder2 = XposedHelpers.callMethod(oldBuilder1, "set", headerTagPrint, "true")
          val newRequestHeaders = XposedHelpers.callMethod(oldBuilder2, "build")
          XposedHelpers.setObjectField(request, "headers", newRequestHeaders)
        }
      }

      override fun afterHookedMethod(param: MethodHookParam) {
        super.afterHookedMethod(param)
        val request = param.args.first()//okhttp3.Request
        val response = param.result//okhttp3.Request
        var url = XposedHelpers.getObjectField(request, "url")?.toString() ?: ""//读取对象 Request.url
        url = MyUtils.formatUrl(url)
        val responseHeaders = XposedHelpers.getObjectField(response, "headers")//读取对象 Request.headers
        val hasPrint = XposedHelpers.callMethod(responseHeaders, "get", headerTagPrint)?.toString() ?: ""//Headers.get(String)
        val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
        val tagInterceptor = XposedHelpers.callMethod(requestHeaders, "get", headerTagInterceptor)?.toString() ?: ""//Headers.get(String)
        val tagInterceptors = XposedHelpers.callMethod(requestHeaders, "get", headerTagInterceptors)?.toString() ?: ""//Headers.get(String)
        val tagUpgrade = XposedHelpers.callMethod(requestHeaders, "get", "Upgrade")?.toString() ?: ""//Headers.get(String)
        var needPrint = false
        if (hasPrint != "true" && tagUpgrade.lowercase() != "websocket" && !MyUtils.isMediaType(url)) {
          needPrint = if (tagInterceptors.isBlank()) true else tagInterceptor == tagInterceptors.split(",").last()//直接在最后一个拦截器打印
        }
        if (needPrint) {
          printResponseInfo(context, param)
          //已经打印需要把当前请求设置为已打印，否则会再次发起同样的请求
          val oldBuilder1 = XposedHelpers.callMethod(responseHeaders, "newBuilder") //Headers.Builder
          val oldBuilder2 = XposedHelpers.callMethod(oldBuilder1, "set", headerTagPrint, "true")
          val newRequestHeaders = XposedHelpers.callMethod(oldBuilder2, "build")
          XposedHelpers.setObjectField(response, "headers", newRequestHeaders)
        }
      }
    })
    //</editor-fold>
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="监听拦截器Interceptor.intercept(Chain),将当前是什么拦截器设置到Header">
  private val mInterceptorList: MutableList<Class<*>> = mutableListOf()
  private fun hookInterceptor(list: MutableList<Class<*>>, clsChain: Class<*>) {
    for (cls in list) {
      if (!mInterceptorList.contains(cls)) {
        mInterceptorList.add(cls)
        XposedHelpers.findAndHookMethod(cls, "intercept", clsChain, object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam) {
            super.beforeHookedMethod(param)
            val interceptor = param.thisObject//okhttp3.Interceptor的实现类
            val chain = param.args.first()
            val request = XposedHelpers.callMethod(chain, "request")//Chain.request()
            val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
            val oldBuilder1 = XposedHelpers.callMethod(requestHeaders, "newBuilder") //Headers.Builder
            val oldBuilder2 = XposedHelpers.callMethod(oldBuilder1, "set", headerTagInterceptor, interceptor.javaClass.name.toString())
            val newRequestHeaders = XposedHelpers.callMethod(oldBuilder2, "build")
            XposedHelpers.setObjectField(request, "headers", newRequestHeaders)
          }
        })
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="打印请求信息">
  private fun printRequestInfo(param: XC_MethodHook.MethodHookParam) {
    val request = param.args.first()//okhttp3.Request
    var url = XposedHelpers.getObjectField(request, "url")?.toString() ?: ""//读取对象 Request.url
    url = MyUtils.formatUrl(url)
    if (!url.lowercase().startsWith("http")) {
      XposedBridge.log("$TAG 请求地址异常:$url")
      return
    }
    val isMedia = MyUtils.isMediaType(url)
    if (isMedia) {//媒体类不打印
      XposedBridge.log("$TAG 媒体类不打印:$url")
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
      val bodyStr = MyUtils.unescapeJson(requestParams)
      sb.append("\n\n请求体:\n")
        .append(if (requestParams.isBlank()) "有请求Body,可能被混淆了，无法读取" else MyUtils.unescapeJson(MyUtils.jsonFormat(bodyStr)))
    }
    sb.append("\n<<<<<<<<Okhttp.Request.END<<<-------------------------------------")
    //打印
    //XposedBridge.log("$TAG 简单打印  接口请求: $method $url")//单独打印接口请求信息
    XposedBridge.log(sb.toString())
    //设置请求时间
    val tags = XposedHelpers.getObjectField(request, "tags") as Map<Class<*>, Any>
    val newMaps: MutableMap<Class<*>, Any> = mutableMapOf()
    tags.entries.forEach { m -> newMaps[m.key] = m.value }
    newMaps[java.lang.Long::class.java] = java.lang.Long.valueOf(System.currentTimeMillis())
    XposedHelpers.setObjectField(request, "tags", newMaps)
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="打印响应信息">
  private fun printResponseInfo(context: Context, param: XC_MethodHook.MethodHookParam) {
    val request = param.args.first()//okhttp3.Request
    var url = XposedHelpers.getObjectField(request, "url")?.toString() ?: ""//读取对象 Request.url
    url = MyUtils.formatUrl(url)
    val method = XposedHelpers.getObjectField(request, "method")?.toString() ?: ""//读取对象 Request.method
    val startTime = XposedHelpers.callMethod(request, "tag", java.lang.Long::class.java)?.toString()?.toLong() ?: 0L
    val currentTime = System.currentTimeMillis()
    val tookMs = currentTime - startTime
    val result = param.result
    var responseBodyStr = ""
    if (param.throwable != null) {//响应失败
      XposedBridge.log("$TAG 简单打印 请求失败：$method ${tookMs}ms $url ${param.throwable.message}")//单独打印接口请求信息
    } else {//响应成功
      val isMedia = MyUtils.isMediaType(url)
      if (isMedia) {//媒体类不打印
        XposedBridge.log("$TAG 媒体类不打印:$url")
        return
      }
      //出参okhttp3.Response
      result?.let { response ->//OKHttp从3.3.0起添加了sentRequestAtMillis和receivedResponseAtMillis
        //请求结果
        val responseHeaders = XposedHelpers.getObjectField(response, "headers").toString().trim()//读取对象 Response.headers
        val sb = StringBuilder()
        sb.append(">>>>>>>>Okhttp.Response.START>>>----------------------------------->")
        //XposedBridge.log("$TAG 简单打印 接口响应: $method ${tookMs}ms $url")//单独打印接口响应时长
        sb.append("\n响应接口:  ").append(method).append("  ").append(tookMs).append("ms").append("  ").append(url)
          .append("\n\n响应头:\n").append(responseHeaders)
        val responseBody = XposedHelpers.getObjectField(response, "body")//Response.ResponseBody
        responseBody?.let { bo ->
          responseBodyStr = responseBody2String(responseHeaders, bo)
          val bodyJsonStr = MyUtils.jsonFormat(if (responseBodyStr.contains(MyUtils.gzipTag)) MyUtils.unGzip(responseBodyStr) else responseBodyStr)//GZIP解密+Json格式化显示
          sb.append("\n\n响应体:\n").append(bodyJsonStr)//美化后的响应体
        }
        sb.append("\n<<<<<<<<Okhttp.Response.END<<<-------------------------------------")
        //打印
        XposedBridge.log(sb.toString())
      }
    }
    val requestBody = XposedHelpers.getObjectField(request, "body")
    val info = LogInfoBan(
      url = url,
      method = method,
      requestSysTime = startTime,
      requestHeader = XposedHelpers.getObjectField(request, "headers").toString().trim(),
      requestParams = if (requestBody == null) "" else requestBody2String(requestBody),
      responseSysTime = currentTime,
      responseHeader = if (result != null) XposedHelpers.getObjectField(request, "headers").toString().trim() else "",
      responseBody = responseBodyStr,
      responseError = param.throwable,
    )
    MyBroadcastReceiver.sendBroadcastWithData(context, info)
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Request.body的String【未格式化】">
  private fun requestBody2String(requestBody: Any): String {//返回原始的请求体
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
  private fun responseBody2String(responseHeaders: String, responseBody: Any): String {//返回原始的响应体,或者失败的描述
    val mediaType = XposedHelpers.callMethod(responseBody, "contentType")//ResponseBody.contentType()
    return if (mediaType != null) {
      val subtype = XposedHelpers.getObjectField(mediaType, "subtype")//MediaType.subtype
      if (MyUtils.isCanParsable(subtype?.toString())) {//通过响应类型判断，可以解析的才解析
        //响应头处理
        val headerMaps = mutableMapOf<String, String>()
        for (s in responseHeaders.split("\n")) {
          if (s.contains(":")) {
            val split = s.split(":")
            headerMaps[split[0].trim()] = split[1].trim()
          }
        }
        //数据是否压缩
        var isGzip = headerMaps["Content-Encoding"]?.lowercase() == "gzip"
        if (!isGzip) isGzip = headerMaps["content-encoding"]?.lowercase() == "gzip"
        //https://github.com/square/okhttp/blob/parent-3.10.0/okhttp-logging-interceptor/src/main/java/okhttp3/logging/HttpLoggingInterceptor.java
        val bufferedSource = XposedHelpers.callMethod(responseBody, "source")//ResponseBody.source()
        XposedHelpers.callMethod(bufferedSource, "request", Long.MAX_VALUE)//BufferedSource.request(Long)
        var buffer = XposedHelpers.callMethod(bufferedSource, "buffer") //BufferedSource.buffer()
        if (isGzip) {
          val cloneBuffer = XposedHelpers.callMethod(buffer, "clone") //Buffer.clone()
          val clsGzip = XposedHelpers.findClassIfExists("okio.GzipSource", lpparam.classLoader)
          val clsBuffer = XposedHelpers.findClassIfExists("okio.Buffer", lpparam.classLoader)
          val gzipSource = XposedHelpers.newInstance(clsGzip, cloneBuffer)//GzipSource(source: Source)
          if (gzipSource != null) {
            buffer = XposedHelpers.newInstance(clsBuffer)//Buffer()
            XposedHelpers.callMethod(buffer, "writeAll", gzipSource)//buffer.writeAll(gzipSource)
            XposedHelpers.callMethod(gzipSource, "close")//gzipSource.close()
          }
        }
        val charset = XposedHelpers.callMethod(mediaType, "charset", Charsets.UTF_8) ?: Charsets.UTF_8//contentType.charset(UTF_8)
        val cloneBuffer = XposedHelpers.callMethod(buffer, "clone") //Buffer.clone()
        val result = XposedHelpers.callMethod(cloneBuffer, "readString", charset)?.toString() ?: "" //Buffer.readString(Charsets.UTF_8)
        result.ifBlank { "有响应Body,可能被混淆了，无法读取" }
      } else {
        "不解析该类型的响应体:${mediaType}"
      }
    } else {
      "没有contentType，不解析响应体"
    }
  }
  //</editor-fold>
}