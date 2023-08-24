package cc.abase.lsposed.utils

import android.app.ActivityManager
import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import de.robv.android.xposed.XposedBridge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream


/**
 * Author:Khaos
 * Date:2023/8/24
 * Time:17:18
 */
object MyUtils {
  val myGsonFormat = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
  val myGsonPrint = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

  fun <T> fromJson(json: String, type: Class<T>?): T {
    return myGsonFormat.fromJson(json, type)
  }

  fun toJson(json: Any): String {
    return myGsonFormat.toJson(json)
  }

  //判断当前进行是否是需要配置的主进程
  fun isMainProcess(context: Context, packageNames: MutableList<String>): Boolean {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { am ->
      val pid = android.os.Process.myPid()
      for (process in am.runningAppProcesses) {
        if (process.pid == pid) {
          return packageNames.any { a -> a == process.processName }
        }
      }
    }
    return false
  }

  //获取当前进程包名
  fun currentProcessPackage(context: Context): String {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { am ->
      val pid = android.os.Process.myPid()
      for (process in am.runningAppProcesses) {
        if (process.pid == pid) {
          return process.processName
        }
      }
    }
    return ""
  }

  //GZIP解压缩
  fun decompressGzipString(gzipString: String): String {
    val compressedData = gzipString.toByteArray()
    val inputStream = ByteArrayInputStream(compressedData)
    val gzipInputStream = GZIPInputStream(inputStream)
    val outputStream = ByteArrayOutputStream()

    val buffer = ByteArray(1024)
    var bytesRead = gzipInputStream.read(buffer)
    while (bytesRead != -1) {
      outputStream.write(buffer, 0, bytesRead)
      bytesRead = gzipInputStream.read(buffer)
    }
    gzipInputStream.close()
    outputStream.close()
    return outputStream.toString()
  }

  //Json数据格式化显示
  fun jsonFormat(s: String): String {
    if (s.isBlank()) {
      return ""
    }
    return try {
      val json = s.trim()
      if (json.startsWith("{") || json.startsWith("[")) myGsonPrint.toJson(JsonParser.parseString(json)) else json
    } catch (e: Exception) {
      e.printStackTrace()
      s
    }
  }

  //判断数据是否可以解析，不能解析的不要解析Body
  fun isCanParsable(subtype: String?): Boolean {
    val type = subtype?.lowercase() ?: ""
    return when {
      type.contains("text") -> true
      type.contains("plain") -> true
      type.contains("json") -> true
      type.contains("xml") -> true
      type.contains("html") -> true
      type.contains("x-www-form-urlencoded") -> true
      else -> false
    }
  }

  //是否是多媒体地址
  fun isMediaType(url: String): Boolean {
    val pic = ".*?(gif|jpeg|png|jpg|bmp)"
    val video = ".*?(avi|rmvb|rm|asf|divx|mpg|mpeg|mpe|wmv|mp4|mkv|vob)"
    return Pattern.compile(pic).matcher(url.lowercase()).matches() || Pattern.compile(video).matcher(url.lowercase()).matches()
  }

  //去除转义
  fun unescapeJson(tag: String, json: String): String {
    //XposedBridge.log("$tag 转义前数据:$json")
    return if (json.startsWith("{") || json.startsWith("[")) {
      val parseString = JsonParser.parseString(json)
      if (parseString.isJsonPrimitive) parseString.asJsonPrimitive.asString else json
    } else {
      json
    }
  }
}