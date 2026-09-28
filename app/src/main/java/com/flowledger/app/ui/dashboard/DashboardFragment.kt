package com.flowledger.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.AccountWithBalance
import com.flowledger.app.databinding.FragmentDashboardBinding
import com.flowledger.app.ui.adapters.AccountCardAdapter
import com.flowledger.app.ui.dialogs.AddAccountBottomSheet
import com.flowledger.app.ui.dialogs.LedgerManagerBottomSheet
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private lateinit var assetAdapter: AccountCardAdapter
    private lateinit var liabilityAdapter: AccountCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerViews() {
        assetAdapter = AccountCardAdapter { item ->
            showAccountActionDialog(item)
        }
        binding.rvAssetAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssetAccounts.adapter = assetAdapter

        liabilityAdapter = AccountCardAdapter { item ->
            showAccountActionDialog(item)
        }
        binding.rvLiabilityAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLiabilityAccounts.adapter = liabilityAdapter
    }

    private fun setupListeners() {
        binding.btnCurrentBook.setOnClickListener {
            val sheet = LedgerManagerBottomSheet()
            sheet.show(parentFragmentManager, "LedgerManagerBottomSheet")
        }

        binding.btnAddAssetAccount.setOnClickListener {
            val sheet = AddAccountBottomSheet.newInstance(AccountType.ASSET)
            sheet.show(parentFragmentManager, "AddAssetAccountBottomSheet")
        }

        binding.btnAddLiabilityAccount.setOnClickListener {
            val sheet = AddAccountBottomSheet.newInstance(AccountType.LIABILITY)
            sheet.show(parentFragmentManager, "AddLiabilityAccountBottomSheet")
        }
    }

    private fun observeData() {
        // 当前激活账本
        viewModel.activeBook.observe(viewLifecycleOwner) { book ->
            val bookName = book?.name ?: "默认账本"
            binding.btnCurrentBook.text = "📖 $bookName ▾"
        }

        // 财务大盘
        viewModel.financialSummary.observe(viewLifecycleOwner) { summary ->
            if (summary != null) {
                binding.tvNetWorth.text = String.format(Locale.getDefault(), "¥ %.2f", summary.netWorth)
                binding.tvTotalAssets.text = String.format(Locale.getDefault(), "¥ %.2f", summary.totalAssets)
                binding.tvTotalLiabilities.text = String.format(Locale.getDefault(), "¥ %.2f", summary.totalLiabilities)
            }
        }

        // 账户列表
        viewModel.assetAccounts.observe(viewLifecycleOwner) { list ->
            assetAdapter.submitList(list)
        }

        viewModel.liabilityAccounts.observe(viewLifecycleOwner) { list ->
            liabilityAdapter.submitList(list)
        }
    }

    private fun showAccountActionDialog(item: AccountWithBalance) {
        val account = item.account
        val context = requireContext()

        val balanceInfo = if (account.type == AccountType.LIABILITY) {
            "当前欠款: ¥${String.format(Locale.getDefault(), "%.2f", item.debtAmount)} | 剩余可用: ¥${String.format(Locale.getDefault(), "%.2f", item.availableCredit)}"
        } else {
            "当前余额: ¥${String.format(Locale.getDefault(), "%.2f", item.currentBalance)}"
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(account.name)
            .setMessage("$balanceInfo\n\n请选择对该账户的操作：")
            .setPositiveButton("删除账户") { _, _ ->
                checkAndDeleteAccount(account.id, account.name)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun checkAndDeleteAccount(accountId: String, accountName: String) {
        val context = requireContext()
        viewModel.checkAccountTransactionCount(accountId) { count ->
            if (count == 0) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("确认删除")
                    .setMessage("确定删除账户【$accountName】吗？该账户尚无任何交易流水，删除将直接生效。")
                    .setPositiveButton("确认删除") { _, _ ->
                        viewModel.deleteAccount(
                            accountId,
                            cascade = false,
                            onSuccess = {
                                Toast.makeText(context, "已删除账户【$accountName】", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                val choices = arrayOf(
                    "归档停用 (推荐：保留历史流水与账目守恒，不再展示在输入选项中)",
                    "彻底级联删除 (警告：将连同关联的 $count 笔交易流水彻底删除)"
                )
                MaterialAlertDialogBuilder(context)
                    .setTitle("账户【$accountName】已有 $count 笔流水")
                    .setItems(choices) { _, which ->
                        when (which) {
                            0 -> {
                                viewModel.archiveAccount(accountId) {
                                    Toast.makeText(context, "已成功归档账户【$accountName】", Toast.LENGTH_SHORT).show()
                                }
                            }
                            1 -> {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle("二次确认彻底删除")
                                    .setMessage("此操作将永久删除账户【$accountName】及其所有的 $count 笔关联交易分录，无法撤销！是否继续？")
                                    .setPositiveButton("永久删除") { _, _ ->
                                        viewModel.deleteAccount(
                                            accountId,
                                            cascade = true,
                                            onSuccess = {
                                                Toast.makeText(context, "已彻底删除账户及所有关联流水", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
