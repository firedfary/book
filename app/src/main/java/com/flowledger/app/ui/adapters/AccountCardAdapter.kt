package com.flowledger.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.AccountWithBalance
import com.flowledger.app.databinding.ItemAccountCardBinding
import java.util.Locale

class AccountCardAdapter(
    private val onItemClick: ((AccountWithBalance) -> Unit)? = null
) : ListAdapter<AccountWithBalance, AccountCardAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccountCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAccountCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AccountWithBalance) {
            val account = item.account
            binding.tvAccountName.text = account.name

            try {
                binding.viewColorTag.setBackgroundColor(Color.parseColor(account.colorHex))
            } catch (e: Exception) {
                binding.viewColorTag.setBackgroundColor(Color.BLUE)
            }

            if (account.type == AccountType.LIABILITY) {
                // 负债账户：展示欠款与剩余可用额度
                val debt = item.debtAmount
                binding.tvAccountBalance.text = String.format(Locale.getDefault(), "欠款: ¥%.2f", debt)
                binding.tvAccountBalance.setTextColor(Color.parseColor("#D32F2F"))

                binding.layoutLiabilityExtra.visibility = View.VISIBLE
                binding.tvCreditLimit.text = String.format(
                    Locale.getDefault(),
                    "剩余可用: ¥%.2f (总额度 ¥%.0f)",
                    item.availableCredit,
                    account.creditLimit
                )

                val billInfo = StringBuilder()
                if (account.billingDay != null) {
                    billInfo.append("账单日: ").append(account.billingDay).append("日 ")
                }
                if (account.repaymentDay != null) {
                    billInfo.append("| 还款日: ").append(account.repaymentDay).append("日")
                }
                binding.tvBillingRepayDays.text = billInfo.toString()
            } else {
                // 资产账户：正常展示余额
                binding.tvAccountBalance.text = String.format(
                    Locale.getDefault(),
                    "¥ %.2f",
                    item.currentBalance
                )
                binding.tvAccountBalance.setTextColor(Color.parseColor("#1A1C1E"))
                binding.layoutLiabilityExtra.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<AccountWithBalance>() {
            override fun areItemsTheSame(oldItem: AccountWithBalance, newItem: AccountWithBalance): Boolean {
                return oldItem.account.id == newItem.account.id
            }

            override fun areContentsTheSame(oldItem: AccountWithBalance, newItem: AccountWithBalance): Boolean {
                return oldItem == newItem
            }
        }
    }
}
