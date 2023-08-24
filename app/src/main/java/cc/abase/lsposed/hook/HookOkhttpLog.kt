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
        if (!pa.contains(":")) hookLog(context, pa)//防止有推送等进程，比如-->com.abase.s1:pushcore
      }
    })
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="Hook打印请求日志">
  private fun hookLog(context: Context, pa: String) {
    XposedBridge.log("当前Hook进程包名:$pa")
    //真正Hook的类
    val classRealChain = XposedHelpers.findClassIfExists("okhttp3.internal.http.RealInterceptorChain", lpparam.classLoader)
    //需要Hook的参数
    val classRequest = XposedHelpers.findClassIfExists("okhttp3.Request", lpparam.classLoader)
    if (classRealChain == null || classRequest == null) {
      XposedBridge.log("Hook失败，没有使用Okhttp或者被混淆了")
      return
    }
    XposedHelpers.findAndHookMethod(classRealChain, "proceed", classRequest, object : XC_MethodHook() {
      //<editor-fold defaultstate="collapsed" desc="防快速请(或者多拦截器)求多次打印">
      //请求耗时处理(TAG+请求开始时间)【请求成功会清理】
      private val mTimeMaps = mutableMapOf<String, Long>()

      //防止多个拦截器重复打印请求(TAG+上次请求时间)【不清理】
      private val mRequestMaps = mutableMapOf<String, Long>()
      //</editor-fold>

      //<editor-fold defaultstate="collapsed" desc="请求参数打印">
      override fun beforeHookedMethod(param: MethodHookParam?) {
        super.beforeHookedMethod(param)
        if (param == null) return
        //入参okhttp3.Request
        param.args.firstOrNull()?.let { request ->
          //读取参数
          val url = XposedHelpers.getObjectField(request, "url")//读取对象 Request.url
          if (!(url?.toString() ?: "").lowercase().startsWith("http")) XposedBridge.log("Hook异常请求地址:$url")
          val isMedia = MyUtils.isMediaType(url?.toString() ?: "")
          val method = XposedHelpers.getObjectField(request, "method")//读取对象 Request.method
          val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
          val requestBody = XposedHelpers.getObjectField(request, "body")//读取对象 Request.body
          if (isMedia) return//媒体类不打印
          if (requestHeaders.toString().isBlank()) return//没有请求头的默认非接口请求 Request.url
          //请求参数
          val sb = StringBuilder()
          sb.append(">>>>>>>>Okhttp.Request.START>>>----------------------------------->")
            .append("\n请求接口:  ").append(method).append("  ").append(url)
            .append("\n\n请求头:\n").append(requestHeaders.toString().trim())
          var requestParams = ""
          requestBody?.let { bo ->
            val classBuffer = XposedHelpers.findClassIfExists("okio.Buffer", lpparam.classLoader)//判断是否存在okio.Buffer，如果被混淆则无法获取到，则需要修改新路径
            if (classBuffer != null) {
              val obBuffer = classBuffer.newInstance()//先把数据写入Buffer，再从先把数据写入Buffer读取
              XposedHelpers.callMethod(bo, "writeTo", obBuffer)//写入：RequestBody.writeTo(okio.Buffer())
              val temp = XposedHelpers.callMethod(obBuffer, "readString", Charsets.UTF_8)//读取：okio.Buffer.readString(Charsets.UTF_8)
              val bodyStr = MyUtils.unescapeJson(temp?.toString() ?: "")
              requestParams = MyUtils.jsonFormat(bodyStr)
              sb.append("\n\n请求体:\n")
                .append(requestParams)
                .append("\n")
            } else {
              sb.append("\n\n请求体:\n")
                .append("有请求Body,可能被混淆了，无法读取")
                .append("\n")
            }
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
      }
      //</editor-fold>

      //<editor-fold defaultstate="collapsed" desc="响应参数打印">
      override fun afterHookedMethod(param: MethodHookParam?) {
        super.afterHookedMethod(param)
        if (param == null) return
        //出参okhttp3.Response
        param.result?.let { response ->
          //读取参数
          val request = XposedHelpers.getObjectField(response, "request")//读取对象 Request
          val requestHeaders = XposedHelpers.getObjectField(request, "headers")//读取对象 Request.headers
          if (requestHeaders.toString().isBlank()) return//没有请求头的默认非接口请求
          val method = XposedHelpers.getObjectField(request, "method")//读取对象 Request.method
          val url = XposedHelpers.getObjectField(request, "url")//读取对象 Request.url
          val requestBody = XposedHelpers.getObjectField(request, "body")//读取对象 Request.body
          var requestParams = ""
          requestBody?.let { bo ->
            val classBuffer = XposedHelpers.findClassIfExists("okio.Buffer", lpparam.classLoader)//判断是否存在okio.Buffer，如果被混淆则无法获取到，则需要修改新路径
            if (classBuffer != null) {
              val obBuffer = classBuffer.newInstance()//先把数据写入Buffer，再从先把数据写入Buffer读取
              XposedHelpers.callMethod(bo, "writeTo", obBuffer)//写入：RequestBody.writeTo(okio.Buffer())
              val temp = XposedHelpers.callMethod(obBuffer, "readString", Charsets.UTF_8)//读取：okio.Buffer.readString(Charsets.UTF_8)
              val bodyStr = MyUtils.unescapeJson(temp?.toString() ?: "")
              requestParams = MyUtils.jsonFormat(bodyStr)
            }
          }
          val tag = "${url.hashCode()}_${requestParams.hashCode()}".replace("-", "")
          if (!mTimeMaps.containsKey(tag)) return
          val startTime = mRequestMaps[tag] ?: 0L
          val currentTime = System.nanoTime()
          val tookMs = TimeUnit.NANOSECONDS.toMillis(currentTime - startTime)
          mTimeMaps.remove(tag)
          val responseHeaders = XposedHelpers.getObjectField(response, "headers")//读取对象 Response.headers
          //请求结果
          val sb = StringBuilder()
          sb.append(">>>>>>>>Okhttp.Response.START>>>----------------------------------->")
          //XposedBridge.log("接口响应: $method ${tookMs}ms $url")//单独过滤响应请求时长
          sb.append("\n响应接口:  ").append(method).append("  ").append(tookMs).append("ms").append("  ").append(url)
            .append("\n\n响应头:\n").append(responseHeaders.toString().trim())
          val responseBody = XposedHelpers.getObjectField(response, "body")//Response.ResponseBody
          responseBody?.let { bo ->
            val mediaType = XposedHelpers.callMethod(bo, "contentType")//ResponseBody.contentType()
            if (mediaType != null) {
              val subtype = XposedHelpers.getObjectField(mediaType, "subtype")//MediaType.subtype
              if (MyUtils.isCanParsable(subtype?.toString())) {//通过响应类型判断，可以解析的才解析
                //响应头处理
                val headerMaps = mutableMapOf<String, String>()
                for (s in responseHeaders.toString().split("\n")) {
                  if (s.contains(":")) {
                    val split = s.split(":")
                    headerMaps[split[0]] = split[1]
                  }
                }
                //数据是否压缩
                val isGzip = headerMaps["Content-Encoding"]?.lowercase() == "gzip"
                val bufferedSource = XposedHelpers.callMethod(bo, "source")//ResponseBody.source()
                XposedHelpers.callMethod(bufferedSource, "request", Long.MAX_VALUE)//BufferedSource.request(Long)
                val buffer = XposedHelpers.callMethod(bufferedSource, "buffer") //BufferedSource.buffer()
                val newBuffer = XposedHelpers.callMethod(buffer, "clone") //Buffer.clone()
                val result = XposedHelpers.callMethod(newBuffer, "readString", Charsets.UTF_8) //Buffer.readString(Charsets.UTF_8)
                if (result != null) {
                  val bodyStr = MyUtils.unescapeJson(result.toString())
                  val info = if (isGzip) MyUtils.decompressGzipString(bodyStr) else bodyStr
                  sb.append("\n\n响应体:\n").append(MyUtils.jsonFormat(info))
                } else {
                  sb.append("\n\n响应体:\n").append("有响应Body,暂时没有读取")
                }
              } else {
                sb.append("\n\n响应体:\n").append("不解析该类型的响应体:${mediaType}")
              }
            }
          }
          sb.append("\n<<<<<<<<Okhttp.Response.END<<<-------------------------------------")
          //打印
          XposedBridge.log(sb.toString())
        }
      }
      //</editor-fold>
    })
  }
  //</editor-fold>
}