package cc.abase.lsposed.bean

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:10:19
 */
data class LogInfoBan(
  val url: String = "",
  val method: String = "",
  var dataSize: String = "",

  val requestSysTime: Long = 0,
  val requestHeader: String = "",
  var requestParams: String = "",

  val responseSysTime: Long = 0,
  val responseHeader: String = "",
  var responseBody: String = "",
  val responseError: Throwable? = null,
)