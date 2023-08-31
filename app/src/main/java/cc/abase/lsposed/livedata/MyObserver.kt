package cc.abase.lsposed.livedata

import de.robv.android.xposed.XposedBridge

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:11:09
 */
open class MyObserver<T>(private val changed: (t: T) -> Unit) : androidx.lifecycle.Observer<T> {
  override fun onChanged(value: T) {
    try {
      changed.invoke(value)
    } catch (e: Exception) {
      XposedBridge.log(e)
    }
  }
}