package cc.abase.lsposed.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage


/**
 * 不能进行混淆，否则会导致找不到该文件
 * https://blog.csdn.net/m0_68075044/article/details/130163627
 * Author:Khaos
 * Date:2023/8/23
 * Time:16:27
 */
class MyLSPosedModule : IXposedHookLoadPackage {
  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
    if (lpparam == null) return
    HookOkhttpLog(lpparam)//网络日志拦截打印
  }
}