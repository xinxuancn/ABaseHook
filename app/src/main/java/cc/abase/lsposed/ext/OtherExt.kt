package cc.abase.lsposed.ext

import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Author:Khaos
 * Date:2023/9/1
 * Time:10:51
 */
inline fun launchError(
  context: CoroutineContext = Dispatchers.Main,//如果使用了IO线程，则异常的时候需要注意也在IO线程
  crossinline handler: (CoroutineContext, Throwable) -> Unit = { _, e -> Log.e("launchError", e.message ?: "") },
  start: CoroutineStart = CoroutineStart.DEFAULT,
  noinline block: suspend CoroutineScope.() -> Unit
): Job {
  return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    .launch(context + CoroutineExceptionHandler(handler), start, block)
}