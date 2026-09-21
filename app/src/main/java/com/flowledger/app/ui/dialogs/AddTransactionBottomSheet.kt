package com.flowledger.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.R
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.databinding.DialogAddTransactionBinding
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

class AddTransactionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogAddTransactionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private var currentMode = 0 // 0: 支出, 1: 收入, 2: 资金流动/还款

    private var allAccounts: List<AccountEntity> = emptyList()
    private var assetAccounts: List<AccountEntity> = emptyList()
    private var liabilityAccounts: List<AccountEntity> = emptyList()
    private var expenseAccounts: List<AccountEntity> = emptyList()
    private var incomeAccounts: List<AccountEntity> = emptyList()

    private var feeAccount: AccountEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeAccounts()
    }

    private fun setupListeners() {
        binding.tabLayoutTxType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = tab?.position ?: 0
                updateFormVisibility()
                refreshSpinners()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.switchHasFee.setOnCheckedChangeListener { _, isChecked ->
            binding.tilFeeAmount.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnSaveTransaction.setOnClickListener {
            handleSave()
        }
    }

    private fun observeAccounts() {
        viewModel.allAccountsWithBalances.observe(viewLifecycleOwner) { list ->
            allAccounts = list.map { it.account }
            assetAccounts = allAccounts.filter { it.type == AccountType.ASSET }
            liabilityAccounts = allAccounts.filter { it.type == AccountType.LIABILITY }
            feeAccount = allAccounts.find { it.category == AccountCategory.FEE }
            refreshSpinners()
        }

        viewModel.expenseAccounts.observe(viewLifecycleOwner) { list ->
            expenseAccounts = list
            refreshSpinners()
        }

        viewModel.incomeAccounts.observe(viewLifecycleOwner) { list ->
            incomeAccounts = list
            refreshSpinners()
        }
    }

    private fun updateFormVisibility() {
        when (currentMode) {
            0 -> {
                // 支出
                binding.tvLabelFrom.text = "付款账户 (资产/负债)"
                binding.tvLabelTo.text = "支出分类节点"
                binding.layoutTransferSpecific.visibility = View.GONE
                binding.etTitle.hint = "例如: 咖啡、聚餐"
            }
            1 -> {
                // 收入
                binding.tvLabelFrom.text = "收入来源分类"
                binding.tvLabelTo.text = "收款到账账户"
                binding.layoutTransferSpecific.visibility = View.GONE
                binding.etTitle.hint = "例如: 9月工资、理财分红"
            }
            2 -> {
                // 资金流动 / 还款
                binding.tvLabelFrom.text = "资金转出账户"
                binding.tvLabelTo.text = "资金转入账户"
                binding.layoutTransferSpecific.visibility = View.VISIBLE
                binding.etTitle.hint = "例如: 信用卡还款、微信提现"
            }
        }
    }

    private var fromList: List<AccountEntity> = emptyList()
    private var toList: List<AccountEntity> = emptyList()

    private fun refreshSpinners() {
        val context = context ?: return

        when (currentMode) {
            0 -> {
                // 支出: 付款方可选资产或负债(信用卡/花呗)
                fromList = assetAccounts + liabilityAccounts
                toList = expenseAccounts
            }
            1 -> {
                // 收入: 来源为收入分类，入账为资产
                fromList = incomeAccounts
                toList = assetAccounts
            }
            2 -> {
                // 转账: 转出通常为资产，转入可为资产或负债(还款)
                fromList = assetAccounts
                toList = assetAccounts + liabilityAccounts
            }
        }

        val fromNames = fromList.map { "${it.name} (${getTypeLabel(it.type)})" }
        val toNames = toList.map { "${it.name} (${getTypeLabel(it.type)})" }

        binding.spinnerFromAccount.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            fromNames
        )

        binding.spinnerToAccount.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            toNames
        )
    }

    private fun getTypeLabel(type: AccountType): String {
        return when (type) {
            AccountType.ASSET -> "资产"
            AccountType.LIABILITY -> "负债"
            AccountType.EXPENSE -> "支出"
            AccountType.INCOME -> "收入"
            AccountType.EQUITY -> "权益"
        }
    }

    private fun handleSave() {
        val amountStr = binding.etAmount.text?.toString()?.trim() ?: ""
        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(requireContext(), "请输入有效的金额", Toast.LENGTH_SHORT).show()
            return
        }

        val title = binding.etTitle.text?.toString()?.trim() ?: ""
        val memo = binding.etMemo.text?.toString()?.trim() ?: ""

        val fromIndex = binding.spinnerFromAccount.selectedItemPosition
        val toIndex = binding.spinnerToAccount.selectedItemPosition

        if (fromIndex < 0 || fromIndex >= fromList.size || toIndex < 0 || toIndex >= toList.size) {
            Toast.makeText(requireContext(), "请选择对应的账户节点", Toast.LENGTH_SHORT).show()
            return
        }

        val fromAcc = fromList[fromIndex]
        val toAcc = toList[toIndex]

        when (currentMode) {
            0 -> {
                viewModel.recordExpense(
                    fromAccountId = fromAcc.id,
                    expenseCategoryAccountId = toAcc.id,
                    amount = amount,
                    title = title.ifBlank { toAcc.name },
                    memo = memo,
                    onSuccess = { dismiss() },
                    onError = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
                )
            }
            1 -> {
                viewModel.recordIncome(
                    toAccountId = toAcc.id,
                    incomeCategoryAccountId = fromAcc.id,
                    amount = amount,
                    title = title.ifBlank { fromAcc.name },
                    memo = memo,
                    onSuccess = { dismiss() },
                    onError = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
                )
            }
            2 -> {
                if (fromAcc.id == toAcc.id) {
                    Toast.makeText(requireContext(), "转出与转入不能为同一账户", Toast.LENGTH_SHORT).show()
                    return
                }

                val hasFee = binding.switchHasFee.isChecked
                var fee = 0.0
                if (hasFee) {
                    fee = binding.etFeeAmount.text?.toString()?.toDoubleOrNull() ?: 0.0
                    if (fee < 0) {
                        Toast.makeText(requireContext(), "手续费不能为负数", Toast.LENGTH_SHORT).show()
                        return
                    }
                }

                val isRepayment = binding.switchIsRepayment.isChecked || toAcc.type == AccountType.LIABILITY

                viewModel.recordTransfer(
                    fromAccountId = fromAcc.id,
                    toAccountId = toAcc.id,
                    amount = amount,
                    feeAccountId = feeAccount?.id,
                    feeAmount = fee,
                    isRepayment = isRepayment,
                    title = title.ifBlank {
                        if (isRepayment) "还款至${toAcc.name}" else "${fromAcc.name}转至${toAcc.name}"
                    },
                    memo = memo,
                    onSuccess = { dismiss() },
                    onError = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
