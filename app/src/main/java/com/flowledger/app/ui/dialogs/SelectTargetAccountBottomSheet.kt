package com.flowledger.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.AccountWithBalance
import com.flowledger.app.databinding.BottomSheetSelectTargetAccountBinding
import com.flowledger.app.databinding.ItemSelectTargetAccountBinding
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class SelectTargetAccountBottomSheet(
    private val onAccountSelected: (AccountEntity) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSelectTargetAccountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private var selectedAccount: AccountEntity? = null
    private lateinit var adapter: AccountSelectAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSelectTargetAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AccountSelectAdapter { account ->
            selectedAccount = account
            binding.btnNext.isEnabled = true
        }

        binding.rvAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAccounts.adapter = adapter

        viewModel.allAccountsWithBalances.observe(viewLifecycleOwner) { list ->
            val targetAccounts = list.filter {
                (it.account.type == AccountType.ASSET || it.account.type == AccountType.LIABILITY) &&
                        !it.account.isDeleted && !it.account.isArchived
            }
            adapter.submitList(targetAccounts)
            if (selectedAccount == null && targetAccounts.isNotEmpty()) {
                selectedAccount = targetAccounts.first().account
                adapter.setSelectedId(selectedAccount!!.id)
            }
        }

        binding.btnAddNewAccount.setOnClickListener {
            val addDialog = AddAccountBottomSheet { createdAccount: AccountEntity ->
                selectedAccount = createdAccount
                adapter.setSelectedId(createdAccount.id)
                binding.btnNext.isEnabled = true
                Toast.makeText(requireContext(), "已创建并选中【${createdAccount.name}】", Toast.LENGTH_SHORT).show()
            }
            addDialog.show(parentFragmentManager, "AddAccountBottomSheet")
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnNext.setOnClickListener {
            val acc = selectedAccount
            if (acc == null) {
                Toast.makeText(requireContext(), "请先选择一个目标账户", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dismiss()
            onAccountSelected(acc)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class AccountSelectAdapter(
        private val onSelect: (AccountEntity) -> Unit
    ) : RecyclerView.Adapter<AccountSelectAdapter.ViewHolder>() {

        private val items = mutableListOf<AccountWithBalance>()
        private var selectedAccountId: String? = null

        fun submitList(newList: List<AccountWithBalance>) {
            items.clear()
            items.addAll(newList)
            notifyDataSetChanged()
        }

        fun setSelectedId(id: String) {
            selectedAccountId = id
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSelectTargetAccountBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isChecked = (item.account.id == selectedAccountId)

            holder.binding.tvAccountName.text = item.account.name
            val typeDesc = if (item.account.type == AccountType.ASSET) "资产账户" else "负债账户"
            holder.binding.tvAccountType.text = "$typeDesc · ${item.account.category}"
            holder.binding.tvAccountBalance.text = String.format(Locale.CHINA, "¥%.2f", item.currentBalance)

            holder.binding.rbSelected.isChecked = isChecked
            holder.binding.cardAccount.isChecked = isChecked

            holder.binding.cardAccount.setOnClickListener {
                selectedAccountId = item.account.id
                notifyDataSetChanged()
                onSelect(item.account)
            }
        }

        inner class ViewHolder(val binding: ItemSelectTargetAccountBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
