package com.flowledger.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.flowledger.app.R
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.ImportedBillCandidate
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.databinding.ItemBillImportCandidateBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillImportPreviewAdapter(
    private val availableAccounts: List<AccountEntity>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<BillImportPreviewAdapter.ViewHolder>() {

    private val items = mutableListOf<ImportedBillCandidate>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun submitList(newItems: List<ImportedBillCandidate>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItems(): List<ImportedBillCandidate> = items

    fun selectAll(selected: Boolean) {
        for (item in items) {
            item.isSelected = selected
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBillImportCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(private val binding: ItemBillImportCandidateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ImportedBillCandidate) {
            binding.tvTitle.text = item.title
            binding.tvTime.text = dateFormat.format(Date(item.occurredAt))

            // 金额展示与正负号色彩
            val amountStr = if (item.type == TransactionType.INCOME) {
                String.format(Locale.CHINA, "+%.2f", item.amount)
            } else {
                String.format(Locale.CHINA, "-%.2f", item.amount)
            }
            binding.tvAmount.text = amountStr

            val amountColor = if (item.type == TransactionType.INCOME) {
                ContextCompat.getColor(binding.root.context, R.color.asset_green)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.text_primary)
            }
            binding.tvAmount.setTextColor(amountColor)

            // 运行余额
            if (item.runningBalance != null) {
                binding.tvRunningBalance.visibility = View.VISIBLE
                binding.tvRunningBalance.text = String.format(Locale.CHINA, "余额: %.2f", item.runningBalance)
            } else {
                binding.tvRunningBalance.visibility = View.GONE
            }

            // 复选框
            binding.cbSelect.setOnCheckedChangeListener(null)
            binding.cbSelect.isChecked = item.isSelected
            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                onSelectionChanged()
            }

            // 重复标签
            binding.tvDuplicateTag.visibility = if (item.isDuplicate) View.VISIBLE else View.GONE

            // 分类按钮
            val categoryText = if (item.type == TransactionType.TRANSFER) {
                "转账 ➔ ${item.transferToAccountName ?: "选择账户"}"
            } else {
                "${item.categoryAccountName.ifBlank { "选择分类" }} ▾"
            }
            binding.btnCategory.text = categoryText
            binding.btnCategory.setOnClickListener {
                showCategoryMenu(it, item)
            }

            // 疑似转账提醒横幅
            if (item.isSuspectedTransfer && item.type != TransactionType.TRANSFER) {
                binding.layoutTransferNotice.visibility = View.VISIBLE
                binding.btnSetTransfer.setOnClickListener {
                    showTransferTargetAccountMenu(it, item)
                }
            } else {
                binding.layoutTransferNotice.visibility = View.GONE
            }
        }

        private fun showCategoryMenu(anchor: View, item: ImportedBillCandidate) {
            val popup = PopupMenu(anchor.context, anchor)
            val relevantAccounts = if (item.type == TransactionType.INCOME) {
                availableAccounts.filter { it.type == AccountType.INCOME && !it.isDeleted }
            } else {
                availableAccounts.filter { it.type == AccountType.EXPENSE && !it.isDeleted }
            }

            relevantAccounts.forEachIndexed { index, acc ->
                popup.menu.add(0, index, index, acc.name)
            }

            popup.setOnMenuItemClickListener { menuItem ->
                val selectedAcc = relevantAccounts[menuItem.itemId]
                item.categoryAccountId = selectedAcc.id
                item.categoryAccountName = selectedAcc.name
                item.type = if (selectedAcc.type == AccountType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
                notifyItemChanged(adapterPosition)
                true
            }
            popup.show()
        }

        private fun showTransferTargetAccountMenu(anchor: View, item: ImportedBillCandidate) {
            val popup = PopupMenu(anchor.context, anchor)
            val assetAccounts = availableAccounts.filter {
                (it.type == AccountType.ASSET || it.type == AccountType.LIABILITY) && !it.isDeleted
            }

            assetAccounts.forEachIndexed { index, acc ->
                popup.menu.add(0, index, index, "转至: ${acc.name}")
            }

            popup.setOnMenuItemClickListener { menuItem ->
                val selectedAcc = assetAccounts[menuItem.itemId]
                item.type = TransactionType.TRANSFER
                item.transferToAccountId = selectedAcc.id
                item.transferToAccountName = selectedAcc.name
                notifyItemChanged(adapterPosition)
                onSelectionChanged()
                true
            }
            popup.show()
        }
    }
}
