package cc.abase.lsposed.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.dylanc.viewbinding.base.ViewBindingUtil

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:14:20
 */
abstract class BaseBindAdapter<T, V : ViewBinding>(val mDatas: MutableList<T>, var onItemClick: ((item: T) -> Unit)? = null) : RecyclerView.Adapter<BaseViewHolder<V>>() {
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<V> {
    return BaseViewHolder(ViewBindingUtil.inflateWithGeneric(this, LayoutInflater.from(parent.context), parent, false))
  }

  override fun getItemCount() = mDatas.size

  override fun onBindViewHolder(holder: BaseViewHolder<V>, position: Int) {
    if (onItemClick == null) {
      holder.itemView.setOnClickListener(null)
    } else {
      holder.itemView.setOnClickListener { onItemClick?.invoke(mDatas[position]) }
    }
    fillData(holder, mDatas[position])
  }

  abstract fun fillData(holder: BaseViewHolder<V>, item: T)
}