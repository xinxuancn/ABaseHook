package cc.abase.lsposed.ext

import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:15:34
 */
@OptIn(InternalCoroutinesApi::class, FlowPreview::class)
fun EditText.onDebounceTextChanges(life: Lifecycle, time: Long = 600, onStart: Boolean = false, afterChange: (String) -> Unit) {
  //防止搜索一样的内容
  var lastSearchStr = ""
  val etState = MutableStateFlow("")
  this.doAfterTextChanged { text -> etState.value = (text ?: "").toString() }// 往流里写数据
  if (onStart) {//添加监听时发送控件的文本信息(否则只有变化时才会回调)
    lastSearchStr = (text ?: "").toString()
    afterChange.invoke(lastSearchStr)
  }
  life.coroutineScope.launch {
    etState.debounce(time) // 限流，单位毫秒
      //.filter { it.isNotBlank() } // 空文本过滤掉
      .collect { s ->
        if (lastSearchStr != s) {
          lastSearchStr = s
          afterChange.invoke(s)
        }
      }
  }
}