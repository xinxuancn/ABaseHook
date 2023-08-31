package cc.abase.lsposed.ui

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import cc.abase.lsposed.adapter.LogInfoAdapter
import cc.abase.lsposed.base.BaseBindingActivity
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.databinding.ActivityMainBinding
import cc.abase.lsposed.ext.onDebounceTextChanges
import cc.abase.lsposed.livedata.AppLiveData
import cc.abase.lsposed.livedata.MyObserver
import cc.abase.lsposed.receiver.MyBroadcastReceiver


class MainActivity : BaseBindingActivity<ActivityMainBinding>() {
  //<editor-fold defaultstate="collapsed" desc="变量">
  private var needRefreshInfo = true
  private val mReceiveDatas = mutableListOf<LogInfoBan>()
  private val myReceiver = MyBroadcastReceiver()
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="初始化">
  @SuppressLint("NotifyDataSetChanged")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    registerReceiver(myReceiver, IntentFilter().also { it.addAction(MyBroadcastReceiver.KEY_RECEIVER_ACTION) })
    refreshStatus(true)
    val list = mutableListOf<LogInfoBan>()
    val adapter = LogInfoAdapter(list).also { it.onItemClick = { b -> DetailActivity.startActivity(mContext, b.hashCode()) } }
    binding.recycler.adapter = adapter
    AppLiveData.logNewLiveData.observe(this, MyObserver { d -> addData(adapter, d) })
    binding.tvStart.setOnClickListener { refreshStatus(true) }
    binding.tvPause.setOnClickListener { refreshStatus(false) }
    binding.etSearch.onDebounceTextChanges(lifecycle) { if (mReceiveDatas.isNotEmpty()) addData(adapter, null) }
    binding.tvClear.setOnClickListener {
      mReceiveDatas.clear()
      adapter.mDatas.clear()
      adapter.notifyDataSetChanged()
      binding.etSearch.setText("")
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="更新点击状态">
  private fun refreshStatus(refresh: Boolean) {
    needRefreshInfo = refresh
    binding.tvStart.setTextColor(if (refresh) Color.GRAY else Color.BLACK)
    binding.tvStart.isEnabled = !refresh
    binding.tvPause.setTextColor(if (refresh) Color.BLACK else Color.GRAY)
    binding.tvPause.isEnabled = refresh
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="添加或者刷新列表">
  @SuppressLint("NotifyDataSetChanged")
  private fun addData(adapter: LogInfoAdapter, data: LogInfoBan?) {
    if (!needRefreshInfo) return
    if (data != null) if (mReceiveDatas.isEmpty()) mReceiveDatas.add(data) else mReceiveDatas.add(0, data)
    val filter = binding.etSearch.text.toString().trim()
    adapter.mDatas.clear()
    adapter.mDatas.addAll(if (filter.isBlank()) mReceiveDatas else mReceiveDatas.filter { f -> f.url.contains(filter, true) })
    adapter.notifyDataSetChanged()
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="生命周期">
  override fun onDestroy() {
    super.onDestroy()
    unregisterReceiver(myReceiver)
  }
  //</editor-fold>
}