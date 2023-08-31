package cc.abase.lsposed.livedata

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.receiver.MyBroadcastReceiver

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:10:12
 */
object AppLiveData {
  const val myPackageName = "cc.abase.lsposed"

  //全部日志信息
  val logAllLiveData = MutableLiveData<MutableList<LogInfoBan>>()

  //最新日志信息
  val logNewLiveData = MutableLiveData<LogInfoBan>()

  //不同进程同步消息
  fun sendBroadcastWithData(context: Context, data: String) {
    context.sendBroadcast(Intent(MyBroadcastReceiver.KEY_RECEIVER_ACTION).apply {
      putExtra(MyBroadcastReceiver.KEY_RECEIVER_DATA, data)
    })
  }
}