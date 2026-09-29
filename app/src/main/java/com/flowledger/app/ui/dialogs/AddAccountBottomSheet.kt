package com.flowledger.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.databinding.BottomSheetAddAccountBinding
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

class AddAccountBottomSheet(
    private val onAccountCreated: ((AccountEntity) -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddAccountBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private var initialType: AccountType = AccountType.ASSET

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val initialTabIndex = if (initialType == AccountType.LIABILITY) 1 else 0
        binding.tabLayoutAccountType.getTabAt(initialTabIndex)?.select()
        updateTypeUI(initialTabIndex == 1)
    }

    private fun setupListeners() {
        binding.tabLayoutAccountType.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateTypeUI(tab?.position == 1)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnSaveAccount.setOnClickListener {
            handleSaveAccount()
        }
    }

    private fun updateTypeUI(isLiability: Boolean) {
        if (isLiability) {
            binding.layoutLiabilityOptions.visibility = View.VISIBLE
            binding.tilAccountName.hint = "负债账户名称 (例如: 招商信用卡、花呗、白条)"
        } else {
            binding.layoutLiabilityOptions.visibility = View.GONE
            binding.tilAccountName.hint = "资产账户名称 (例如: 私房钱、工商银行、零钱袋)"
        }
    }

    private fun handleSaveAccount() {
        val name = binding.etAccountName.text?.toString()?.trim() ?: ""
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "请输入账户名称", Toast.LENGTH_SHORT).show()
            return
        }

        val isLiability = binding.tabLayoutAccountType.selectedTabPosition == 1
        val accountType = if (isLiability) AccountType.LIABILITY else AccountType.ASSET
        val defaultCategory = if (isLiability) AccountCategory.CREDIT_CARD else AccountCategory.BANK_CARD

        val initBalanceStr = binding.etInitialBalance.text?.toString()?.trim() ?: "0.0"
        val initialBalance = initBalanceStr.toDoubleOrNull() ?: 0.0

        var creditLimit = 0.0
        var billingDay: Int? = null
        var repaymentDay: Int? = null

        if (isLiability) {
            val limitStr = binding.etCreditLimit.text?.toString()?.trim() ?: "0.0"
            creditLimit = limitStr.toDoubleOrNull() ?: 0.0

            val billDayStr = binding.etBillingDay.text?.toString()?.trim()
            billingDay = billDayStr?.toIntOrNull()

            val repayDayStr = binding.etRepaymentDay.text?.toString()?.trim()
            repaymentDay = repayDayStr?.toIntOrNull()
        }

        val colorHex = if (isLiability) "#D32F2F" else "#1976D2"

        viewModel.addCustomAccount(
            name = name,
            type = accountType,
            category = defaultCategory,
            currency = "CNY",
            initialBalance = initialBalance,
            creditLimit = creditLimit,
            billingDay = billingDay,
            repaymentDay = repaymentDay,
            colorHex = colorHex,
            onSuccess = { createdAccount ->
                Toast.makeText(requireContext(), "成功添加账户【${createdAccount.name}】", Toast.LENGTH_SHORT).show()
                onAccountCreated?.invoke(createdAccount)
                dismiss()
            },
            onError = { err ->
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(type: AccountType): AddAccountBottomSheet {
            return AddAccountBottomSheet().apply {
                initialType = type
            }
        }
    }
}
