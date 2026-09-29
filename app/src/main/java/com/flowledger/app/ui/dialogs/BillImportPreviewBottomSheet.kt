package com.flowledger.app.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.R
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BillParseResult
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.databinding.BottomSheetBillImportPreviewBinding
import com.flowledger.app.ui.adapters.BillImportPreviewAdapter
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class BillImportPreviewBottomSheet(
    private val targetAccount: AccountEntity,
    private val parseResult: BillParseResult
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBillImportPreviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private lateinit var adapter: BillImportPreviewAdapter
    private var availableAccountsList: List<AccountEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBillImportPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTargetAccountBadge.text = "入账账户: ${targetAccount.name}"
        binding.tvSourceInfo.text = "识别渠道：${parseResult.sourceName}"
        binding.tvReconciliationDetail.text = parseResult.reconciliationDetail

        if (parseResult.isReconciled) {
            binding.cardReconciliation.setCardBackgroundColor(0xFFE8F5E9.toInt())
            binding.tvReconciliationDetail.setTextColor(0xFF2E7D32.toInt())
        } else {
            binding.cardReconciliation.setCardBackgroundColor(0xFFFFF3E0.toInt())
            binding.tvReconciliationDetail.setTextColor(0xFFE65100.toInt())
        }

        binding.rvCandidates.layoutManager = LinearLayoutManager(requireContext())

        // 观察账户列表并装配适配器与查重
        viewModel.allAccountsWithBalances.observe(viewLifecycleOwner) { list ->
            availableAccountsList = list.map { it.account }
            if (!::adapter.isInitialized) {
                adapter = BillImportPreviewAdapter(availableAccountsList) {
                    updateSummary()
                }
                binding.rvCandidates.adapter = adapter

                // 执行去重检查
                viewModel.checkDuplicates(targetAccount.id, parseResult.items) { checkedItems ->
                    adapter.submitList(checkedItems)
                    updateSummary()
                }
            }
        }

        // 全选 / 反选
        binding.cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (::adapter.isInitialized) {
                adapter.selectAll(isChecked)
            }
        }

        // 余额校准提示卡片
        if (parseResult.detectedAccountBalance != null) {
            binding.cardBalanceAdjustment.visibility = View.VISIBLE
            val balStr = String.format(Locale.CHINA, "¥%.2f", parseResult.detectedAccountBalance)
            binding.tvAdjustDesc.text = "检测到截图最新账面余额为 $balStr。勾选后将在导入后自动生成一条校准分录，使账户余额与银行对齐。"
        } else {
            binding.cardBalanceAdjustment.visibility = View.GONE
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirmImport.setOnClickListener {
            handleConfirmImport()
        }
    }

    private fun updateSummary() {
        if (!::adapter.isInitialized) return
        val items = adapter.getItems()
        val selected = items.filter { it.isSelected }

        var totalExpense = 0.0
        var totalIncome = 0.0

        for (item in selected) {
            when (item.type) {
                TransactionType.INCOME -> totalIncome += item.amount
                TransactionType.EXPENSE, TransactionType.TRANSFER -> totalExpense += item.amount
                else -> totalExpense += item.amount
            }
        }

        binding.tvSummaryStats.text = String.format(
            Locale.CHINA,
            "已选 %d/%d 笔 | 支出 ¥%.2f | 收入 ¥%.2f",
            selected.size,
            items.size,
            totalExpense,
            totalIncome
        )

        binding.btnConfirmImport.text = "确认导入 (${selected.size} 笔)"
        binding.btnConfirmImport.isEnabled = selected.isNotEmpty()
    }

    private fun handleConfirmImport() {
        val items = adapter.getItems()
        val selectedCount = items.count { it.isSelected }
        if (selectedCount == 0) {
            Toast.makeText(requireContext(), "请至少勾选一笔明细导入", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.importBillCandidates(
            targetAccountId = targetAccount.id,
            candidates = items,
            onSuccess = { importedCount ->
                // 检查是否勾选了余额自动校准
                if (binding.cbAdjustBalance.isChecked && parseResult.detectedAccountBalance != null) {
                    viewModel.adjustAccountBalance(
                        targetAccountId = targetAccount.id,
                        targetBalance = parseResult.detectedAccountBalance,
                        onSuccess = {
                            Toast.makeText(requireContext(), "成功导入 $importedCount 笔流水，已校准最新余额！", Toast.LENGTH_LONG).show()
                        },
                        onError = { err ->
                            Toast.makeText(requireContext(), "成功导入 $importedCount 笔，但余额校准失败: $err", Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    Toast.makeText(requireContext(), "成功导入 $importedCount 笔资金流向记录！", Toast.LENGTH_SHORT).show()
                }
                dismiss()
            },
            onError = { err ->
                Toast.makeText(requireContext(), "导入失败: $err", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
