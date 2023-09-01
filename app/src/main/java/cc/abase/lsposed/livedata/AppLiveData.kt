package cc.abase.lsposed.livedata

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.lifecycle.MutableLiveData
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.ext.launchError
import cc.abase.lsposed.receiver.MyBroadcastReceiver
import cc.abase.lsposed.utils.MyUtils
import kotlinx.coroutines.Dispatchers

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:10:12
 */
object AppLiveData {
  //全部日志信息
  val logAllLiveData = MutableLiveData<MutableList<LogInfoBan>>()

  //最新日志信息
  val logNewLiveData = MutableLiveData<LogInfoBan>()
}