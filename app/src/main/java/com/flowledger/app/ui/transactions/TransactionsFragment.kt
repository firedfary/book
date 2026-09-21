package com.flowledger.app.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.databinding.FragmentTransactionsBinding
import com.flowledger.app.ui.adapters.TransactionListAdapter
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private lateinit var adapter: TransactionListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TransactionListAdapter { item ->
            // 点击可删除或查看详情
            AlertDialog.Builder(requireContext())
                .setTitle("删除记录")
                .setMessage("是否确认删除「${item.title}」这条资金流转记录？相关账户余额将自动回退。")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteTransaction(item.transactionId)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        viewModel.transactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isNullOrEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvTransactions.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvTransactions.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
