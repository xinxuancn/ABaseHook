package cc.abase.lsposed.base

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:14:08
 */
class BaseViewHolder<T : ViewBinding>(val vb: T) : RecyclerView.ViewHolder(vb.root)