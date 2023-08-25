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
      override fun afterHookedMethod(param: MethodHookParam?) {
        if (param == null) return
        val context = param.thisObject as Context
        val pa = MyUtils.currentProcessPackage(context)
        if (pa != "cc.abase.lsposed" && !pa.contains(":")) hookLog(context, pa)//防止有推送等进程，比如-->com.abase.s1:pushcore
      }
    })
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Hook打印请求日志">
  private fun hookLog(context: Context, pa: String) {
    XposedBridge.log("Hook 进程包名:$pa")
    //------------------------------------判断是否使用了OkHttp------------------------------------//
    val clsOkHttp = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient", lpparam.classLoader)
    if (clsOkHttp == null) {
      XposedBridge.log("Hook失败，没有使用Okhttp或者被混淆了")
      return
    }
    //------------------------------------读取OkHttp使用的版本------------------------------------//
    val clsOkVersionOld = XposedHelpers.findClassIfExists("okhttp3.internal.Version", lpparam.classLoader)
    val clsOkVersionNew = XposedHelpers.findClassIfExists("okhttp3.OkHttp", lpparam.classLoader)
    if (clsOkVersionOld != null) {
      val version = XposedHelpers.callMethod(clsOkVersionOld, "userAgent")
      XposedBridge.log("Hook OkHttp版本号:$version")
    } else if (clsOkVersionNew != null) {
      val version = XposedHelpers.getStaticObjectField(clsOkVersionNew, "VERSION")
      XposedBridge.log("Hook OkHttp版本号:$version")
    } else {
      XposedBridge.log("Hook OkHttp版本号获取失败")
    }
    //------------------------------------监听OkHttp构造------------------------------------//
    val clsOkClientBuilder = XposedHelpers.findClass("okhttp3.OkHttpClient\$Builder", lpparam.classLoader)
    var mCountOkHttp = 0//有多少个Okhttp

    XposedHelpers.findAndHookConstructor(clsOkHttp, clsOkClientBuilder, object : XC_MethodHook() {
      override fun beforeHookedMethod(param: MethodHookParam?) {
        super.beforeHookedMethod(param)
        if (param == null) return
        //------------------------------------如果已经添加过我们的拦截器，则不再处理------------------------------------//
        val okHttpClientBuilder = param.args.first()//OkHttpClient.Builder
        val clsCacheInterceptor = XposedHelpers.findClassIfExists("okhttp3.internal.cache.CacheInterceptor", lpparam.classLoader)//okhttp3.internal.cache.CacheInterceptor
        val interceptors = XposedHelpers.callMethod(okHttpClientBuilder, "interceptors") as MutableList<Any>//OkHttpClient.Builder.interceptors()
        val firstDefaultInterceptor = interceptors.firstOrNull()//读取第一个拦截器
        if (firstDefaultInterceptor != null && firstDefaultInterceptor.javaClass == clsCacheInterceptor) {//如果是自己添加的拦截器类型
          val cache = XposedHelpers.getObjectField(firstDefaultInterceptor, "cache")//判断cache对象是否为空，如果为空则表示是我们自己设置的，毕竟真实项目中不太可能设置空cache
          if (cache == null) {
            XposedBridge.log("Hook Okhttp拦截器是自己添加过的，疑似执行了OkHttpClient.newBuilder(),所以不再添加新的拦截器")
            return
          }
        }
        //------------------------------------OkHttpClient计数------------------------------------//
        mCountOkHttp++
        val currentIndexOkHttp = mCountOkHttp//当前是第几个OkHttp
        //------------------------------------初始化自己需添加的拦截器------------------------------------//
        val clsCacheOld = XposedHelpers.findClassIfExists("okhttp3.internal.cache.InternalCache", lpparam.classLoader)
        val clsCacheNew = XposedHelpers.findClassIfExists("okhttp3.Cache", lpparam.classLoader)
        var responseInterceptor: Any? = null//第一个拦截器
        var requestInterceptor: Any? = null//最后一个拦截器
        if (clsCacheOld != null) {//构建旧版本的okhttp3.internal.cache.CacheInterceptor
          responseInterceptor = XposedHelpers.newInstance(clsCacheInterceptor, mutableListOf(clsCacheOld).toTypedArray(), null)
          requestInterceptor = XposedHelpers.newInstance(clsCacheInterceptor, mutableListOf(clsCacheOld).toTypedArray(), null)
        } else if (clsCacheNew != null) {//构建新版本的okhttp3.internal.cache.CacheInterceptor
          responseInterceptor = XposedHelpers.newInstance(clsCacheInterceptor, mutableListOf(clsCacheNew).toTypedArray(), null)
          requestInterceptor = XposedHelpers.newInstance(clsCacheInterceptor, mutableListOf(clsCacheNew).toTypedArray(), null)
        }
        if (responseInterceptor == null || requestInterceptor == null) {
          XposedBridge.log("Hook Okhttp疑似不存在okhttp3.internal.cache.CacheInterceptor")
          return
        }
        //------------------------------------监听拦截器------------------------------------////addInterceptor->Request：先添加先执行；Response：先添加后执行
        if (interceptors.isEmpty()) {
          interceptors.add(responseInterceptor)//添加到第一个，方便最后解析数据
          interceptors.add(requestInterceptor)//添加到倒数第二个，方便在加密前打印
        } else {
          interceptors.add(0, responseInterceptor)//添加到第一个，方便最后解析数据
          interceptors.add(interceptors.size - 1, requestInterceptor)//添加到倒数第二个，方便在加密前打印
        }
        val clsChain = XposedHelpers.findClass("okhttp3.Interceptor.Chain", lpparam.classLoader)//参数类型
        //第一个拦截器
        XposedHelpers.findAndHookMethod(responseInterceptor.javaClass, "intercept", clsChain, object : XC_MethodHook() {
          override fun beforeHookedMethod(param: MethodHookParam?) {
            super.beforeHookedMethod(param)
            if (param == null) return
            printRequestInfo(currentIndexOkHttp, param)//处理之前打印
          }
        })
        //最后一个拦截器
        XposedHelpers.findAndHookMethod(requestInterceptor.javaClass, "intercept", clsChain, object : XC_MethodHook() {
          override fun afterHookedMethod(param: MethodHookParam?) {
            super.afterHookedMethod(param)
            if (param == null) return
            printResponseInfo(currentIndexOkHttp, param)
          }
        })
        return
      }
    })
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="防止重复请求">
  //请求耗时处理(TAG+请求开始时间)【请求成功会清理】
  private val mTimeMaps = mutableMapOf<String, Long>()

  //防止多个拦截器重复打印请求(TAG+上次请求时间)【不清理】
  private val mRequestMaps = mutableMapOf<String, Long>()
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="打印请求信息">
  private fun printRequestInfo(index: Int, param: XC_MethodHook.MethodHookParam) {
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
    val tag = "${url.hashCode()}_${requestParams.hashCode()}".replace("-", "")
    val lastTime = mRequestMaps[tag] ?: 0L
    val currentTime = System.nanoTime()
    val tookMs = TimeUnit.NANOSECONDS.toMillis(currentTime - lastTime)
    if (tookMs < 3000) return//3秒内防止重复请求
    mRequestMaps[tag] = currentTime
    //记录请求时间
    mTimeMaps[tag] = currentTime
    sb.append("\n<<<<<<<<Okhttp.Request.END<<<-------------------------------------")
    //打印
    //XposedBridge.log("接口请求: $method $url $tag ${if (time == 0L) "" else "${tookMs}ms"}")//单独打印哪些接口发起了请求
    XposedBridge.log(sb.toString())
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="打印响应信息">
  private fun printResponseInfo(index: Int, param: XC_MethodHook.MethodHookParam) {
    val request = XposedHelpers.callMethod(param.args.first(), "request")//Chain.request()
    val url = XposedHelpers.getObjectField(request, "url")//读取对象 Request.url
    val method = XposedHelpers.getObjectField(request, "method")//读取对象 Request.method
    val requestBody = XposedHelpers.getObjectField(request, "body")//读取对象 Request.body
    var requestParams = ""
    requestBody?.let { bo -> requestParams = requestBody2String(bo) }
    val tag = "${url.hashCode()}_${requestParams.hashCode()}".replace("-", "")
    if (!mTimeMaps.containsKey(tag)) return//如果没有在请求里，则不进行响应
    if (param.throwable != null) {//响应失败
      XposedBridge.log("第${index}个OkHttp ${request.hashCode()} 请求失败：${url} ${param.throwable.message}")
    } else {//响应成功
      //入参okhttp3.Request
      val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
      if (requestHeaders.toString().isBlank()) return//没有请求头的默认非接口请求
      //出参okhttp3.Response
      param.result?.let { response ->
        val startTime = mRequestMaps[tag] ?: 0L
        val currentTime = System.nanoTime()
        val tookMs = TimeUnit.NANOSECONDS.toMillis(currentTime - startTime)
        mTimeMaps.remove(tag)//有可能出现后请求先响应，这样就会导致时间不准确的情况
        //请求结果
        val responseHeaders = XposedHelpers.getObjectField(response, "headers").toString().trim()//读取对象 Response.headers
        val sb = StringBuilder()
        sb.append(">>>>>>>>Okhttp.Response.START>>>----------------------------------->")
        //XposedBridge.log("接口响应: $method ${tookMs}ms $url")//单独打印过滤响应请求时长
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