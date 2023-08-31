package cc.abase.lsposed.adapter

import android.annotation.SuppressLint
import android.net.Uri
import cc.abase.lsposed.base.BaseBindAdapter
import cc.abase.lsposed.base.BaseViewHolder
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.databinding.ItemInfoParentBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:14:06
 */
class LogInfoAdapter(list: MutableList<LogInfoBan>) : BaseBindAdapter<LogInfoBan, ItemInfoParentBinding>(list) {
  private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

  @SuppressLint("SetTextI18n")
  override fun fillData(holder: BaseViewHolder<ItemInfoParentBinding>, item: LogInfoBan) {
    val t1 = item.requestSysTime
    val t3 = item.responseSysTime - t1
    holder.vb.tvTime.text = sdf.format(t1)
    holder.vb.tvDuration.text = "${t3}ms"
    val url = item.url
    val uri = Uri.parse(url)
    val s1 = uri.scheme
    val s2 = uri.host
    val s12 = "${s1}://${s2}"
    holder.vb.tvHost.text = s12
    holder.vb.tvUrl.text = url.replaceFirst(s12, "")
  }
}