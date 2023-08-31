package cc.abase.lsposed.bean

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:10:19
 */
data class LogInfoBan(
  val url: String = "",
  val method: String = "",

  val requestSysTime: Long = 0,
  val requestHeader: String = "",
  val requestParams: String = "",

  val responseSysTime: Long = 0,
  val responseHeader: String = "",
  val responseBody: String = "",
  val responseError: Throwable? = null,
)