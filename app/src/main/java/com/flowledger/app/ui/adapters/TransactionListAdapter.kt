package com.flowledger.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flowledger.app.data.model.TransactionDisplayItem
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionListAdapter(
    private val onItemClick: ((TransactionDisplayItem) -> Unit)? = null
) : ListAdapter<TransactionDisplayItem, TransactionListAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionDisplayItem) {
            binding.tvTxTitle.text = item.title
            binding.tvTxDate.text = dateFormat.format(Date(item.occurredAt))

            // 资金流向节点标注
            binding.tvFromAccount.text = item.fromAccountName
            binding.tvToAccount.text = item.toAccountName

            // 备注
            if (item.memo.isNotBlank()) {
                binding.tvTxMemo.text = item.memo
                binding.tvTxMemo.visibility = View.VISIBLE
            } else {
                binding.tvTxMemo.visibility = View.GONE
            }

            // 金额与色彩
            val context = binding.root.context
            when (item.type) {
                TransactionType.EXPENSE -> {
                    binding.tvTxAmount.text = String.format(Locale.getDefault(), "- ¥%.2f", item.amount)
                    binding.tvTxAmount.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.flowledger.app.R.color.liability_red))
                }
                TransactionType.INCOME -> {
                    binding.tvTxAmount.text = String.format(Locale.getDefault(), "+ ¥%.2f", item.amount)
                    binding.tvTxAmount.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.flowledger.app.R.color.asset_green))
                }
                TransactionType.TRANSFER, TransactionType.REPAYMENT -> {
                    binding.tvTxAmount.text = String.format(Locale.getDefault(), "¥%.2f", item.amount)
                    binding.tvTxAmount.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.flowledger.app.R.color.transfer_blue))
                }
                else -> {
                    binding.tvTxAmount.text = String.format(Locale.getDefault(), "¥%.2f", item.amount)
                    binding.tvTxAmount.setTextColor(androidx.core.content.ContextCompat.getColor(context, com.flowledger.app.R.color.text_primary))
                }
            }

            // 手续费显示
            if (item.feeAmount != null && item.feeAmount > 0) {
                binding.tvFeeBadge.visibility = View.VISIBLE
                binding.tvFeeBadge.text = String.format(Locale.getDefault(), "含手续费 ¥%.2f", item.feeAmount)
            } else {
                binding.tvFeeBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<TransactionDisplayItem>() {
            override fun areItemsTheSame(oldItem: TransactionDisplayItem, newItem: TransactionDisplayItem): Boolean {
                return oldItem.transactionId == newItem.transactionId
            }

            override fun areContentsTheSame(oldItem: TransactionDisplayItem, newItem: TransactionDisplayItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
