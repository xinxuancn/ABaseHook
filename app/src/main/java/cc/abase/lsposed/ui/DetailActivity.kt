package cc.abase.lsposed.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import cc.abase.lsposed.base.BaseBindingActivity
import cc.abase.lsposed.databinding.ActivityDetailBinding
import cc.abase.lsposed.livedata.AppLiveData
import cc.abase.lsposed.utils.MyUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:11:53
 */
class DetailActivity : BaseBindingActivity<ActivityDetailBinding>() {
  //<editor-fold defaultstate="collapsed" desc="外部跳转">
  companion object {
    private const val INTENT_KEY_HASHCODE = "INTENT_KEY_HASHCODE"
    fun startActivity(context: Context, logInfoBanHashCode: Int) {
      context.startActivity(Intent(context, DetailActivity::class.java).apply {
        putExtra(INTENT_KEY_HASHCODE, logInfoBanHashCode)
      })
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="初始化">
  @SuppressLint("SetTextI18n")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val vb = binding
    val code = intent?.getIntExtra(INTENT_KEY_HASHCODE, -1) ?: -1
    val first = AppLiveData.logAllLiveData.value?.firstOrNull { f -> f.hashCode() == code }
    if (first == null) {
      vb.llContent.visibility = View.GONE
    } else {
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
      val t1 = first.requestSysTime
      val t2 = first.responseSysTime
      val t3 = t2 - t1
      vb.tvRequestTime.text = sdf.format(t1)
      vb.tvRequestUrl.text = first.url
      vb.tvRequestMethod.text = first.method
      vb.tvRequestHeader.text = first.requestHeader
      vb.tvRequestParams.text = MyUtils.unescapeJson(first.requestParams)
      vb.tvRequestHeader.visibility = if (first.requestHeader.isBlank()) View.GONE else View.VISIBLE
      vb.tvRequestParams.visibility = if (first.requestParams.isBlank()) View.GONE else View.VISIBLE
      vb.tvResponseTime.text = sdf.format(t2)
      vb.tvResponseDuration.text = "${t3}ms"
      vb.tvResponseHeader.text = first.responseHeader
      val needDecrypt = first.responseBody.contains(MyUtils.gzipTag)
      vb.tvResponseBody.text = if (needDecrypt) "正在解密数据..." else first.responseBody
      vb.tvResponseError.text = first.responseError?.toString() ?: ""
      vb.tvResponseHeader.visibility = if (first.responseHeader.isBlank()) View.GONE else View.VISIBLE
      vb.tvResponseBody.visibility = if (first.responseBody.isBlank()) View.GONE else View.VISIBLE
      vb.tvResponseError.visibility = if ((first.responseError?.toString() ?: "").isBlank()) View.GONE else View.VISIBLE
      if (needDecrypt) lifecycleScope.launch(Dispatchers.Main) {
        withContext(Dispatchers.IO) { MyUtils.unescapeJson(MyUtils.unGzip(first.responseBody)) }.let { s ->
          vb.tvResponseBody.text = s
        }
      }
    }
  }
  //</editor-fold>
}