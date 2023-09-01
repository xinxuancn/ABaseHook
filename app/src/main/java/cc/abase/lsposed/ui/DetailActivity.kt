package cc.abase.lsposed.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import cc.abase.lsposed.adapter.LineAdapter
import cc.abase.lsposed.base.BaseBindingActivity
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.databinding.ActivityDetailBinding
import cc.abase.lsposed.livedata.AppLiveData
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
    private const val INTENT_KEY_TAG = "INTENT_KEY_TAG"
    fun startActivity(context: Context, bean: LogInfoBan) {
      context.startActivity(Intent(context, DetailActivity::class.java).apply {
        putExtra(INTENT_KEY_TAG, "${bean.url}_${bean.requestSysTime}")
      })
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="初始化">
  @SuppressLint("SetTextI18n")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val vb = binding
    val tag = intent?.getStringExtra(INTENT_KEY_TAG) ?: ""
    val logBean = AppLiveData.logAllLiveData.value?.firstOrNull { f -> "${f.url}_${f.requestSysTime}" == tag }
    if (logBean == null) {
      vb.llContent.visibility = View.GONE
    } else {
      val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
      val t1 = logBean.requestSysTime
      val t2 = logBean.responseSysTime
      val t3 = t2 - t1
      vb.tvRequestTime.text = sdf.format(t1)
      vb.tvRequestUrl.text = logBean.url
      vb.tvRequestMethod.text = logBean.method
      vb.tvRequestHeader.text = logBean.requestHeader
      vb.tvRequestParams.text = logBean.requestParams
      vb.tvRequestHeader.visibility = if (logBean.requestHeader.isBlank()) View.GONE else View.VISIBLE
      vb.tvRequestParams.visibility = if (logBean.requestParams.isBlank()) View.GONE else View.VISIBLE
      vb.tvResponseTime.text = sdf.format(t2)
      vb.tvResponseDuration.text = "${t3}ms"
      vb.tvResponseHeader.text = logBean.responseHeader
      vb.tvResponseError.text = logBean.responseError?.toString() ?: ""
      vb.tvResponseHeader.visibility = if (logBean.responseHeader.isBlank()) View.GONE else View.VISIBLE
      vb.recyclerResponse.visibility = if (logBean.responseBody.isBlank()) View.GONE else View.VISIBLE
      vb.tvResponseError.visibility = if ((logBean.responseError?.toString() ?: "").isBlank()) View.GONE else View.VISIBLE
      val datas = logBean.responseBody.split("\n").toMutableList()
      val adapter = LineAdapter(datas)
      vb.recyclerResponse.adapter = adapter
    }
  }
  //</editor-fold>
}