package cc.abase.lsposed.base

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.dylanc.viewbinding.base.FragmentBinding
import com.dylanc.viewbinding.base.FragmentBindingDelegate

/**
 * https://dylancaicoding.github.io/ViewBindingKTX/#/zh/baseclass?id=fragment
 * Author:Khaos
 * Date:2023/8/31
 * Time:14:30
 */
abstract class BaseBindingFragment<VB : ViewBinding> : Fragment(), FragmentBinding<VB> by FragmentBindingDelegate() {
  protected lateinit var mContext: Context
  
  override fun onAttach(context: Context) {
    super.onAttach(context)
    mContext = context
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return createViewWithBinding(inflater, container)
  }
}
