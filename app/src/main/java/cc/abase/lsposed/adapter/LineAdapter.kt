package cc.abase.lsposed.adapter

import cc.abase.lsposed.base.BaseBindAdapter
import cc.abase.lsposed.base.BaseViewHolder
import cc.abase.lsposed.databinding.ItemResponseLineBinding

/**
 * Author:XX
 * Date:2023/9/1
 * Time:14:58
 */
class LineAdapter(list: MutableList<String>) : BaseBindAdapter<String, ItemResponseLineBinding>(list) {
  override fun fillData(holder: BaseViewHolder<ItemResponseLineBinding>, item: String) {
    holder.vb.tvLine.text = item
  }
}