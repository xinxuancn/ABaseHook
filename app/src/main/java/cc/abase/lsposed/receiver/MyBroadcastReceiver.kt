package cc.abase.lsposed.receiver

import android.content.*
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.livedata.AppLiveData
import cc.abase.lsposed.utils.MyUtils

/**
 * Author:XX
 * Date:2023/8/31
 * Time:17:15
 */


// 创建一个自定义的广播接收器类
class MyBroadcastReceiver : BroadcastReceiver() {
  companion object {
    const val KEY_RECEIVER_DATA = "KEY_RECEIVER_DATA"
    const val KEY_RECEIVER_ACTION = "KEY_RECEIVER_ACTION"
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (context.packageName != AppLiveData.myPackageName) return
    // 在这里处理接收到的广播
    when (intent.action) {
      KEY_RECEIVER_ACTION -> {
        intent.getStringExtra(KEY_RECEIVER_DATA)?.let { s ->
          val info = MyUtils.fromJson(s, LogInfoBan::class.java)
          val list = AppLiveData.logAllLiveData.value
          AppLiveData.logNewLiveData.postValue(info)
          if (list == null) {
            AppLiveData.logAllLiveData.postValue(mutableListOf(info))
          } else {
            list.add(0, info)
            AppLiveData.logAllLiveData.postValue(list.takeLast(1000).toMutableList())//保留最新1000条
          }
        }
      }
    }
  }
}