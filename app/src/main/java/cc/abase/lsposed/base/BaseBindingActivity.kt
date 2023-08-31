package cc.abase.lsposed.base

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.dylanc.viewbinding.base.ActivityBinding
import com.dylanc.viewbinding.base.ActivityBindingDelegate

/**
 * https://dylancaicoding.github.io/ViewBindingKTX/#/zh/baseclass?id=activity
 * Author:Khaos
 * Date:2023/8/31
 * Time:14:28
 */
abstract class BaseBindingActivity<VB : ViewBinding> : AppCompatActivity(), ActivityBinding<VB> by ActivityBindingDelegate() {
  protected lateinit var mContext: Context

  override fun attachBaseContext(newBase: Context?) {
    super.attachBaseContext(newBase)
    mContext = this
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentViewWithBinding()
  }
}
