package com.flowledger.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.databinding.FragmentDashboardBinding
import com.flowledger.app.ui.adapters.AccountCardAdapter
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
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
        observeData()
    }

    private fun setupRecyclerViews() {
        assetAdapter = AccountCardAdapter()
        binding.rvAssetAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAssetAccounts.adapter = assetAdapter

        liabilityAdapter = AccountCardAdapter()
        binding.rvLiabilityAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLiabilityAccounts.adapter = liabilityAdapter
    }

    private fun observeData() {
        viewModel.financialSummary.observe(viewLifecycleOwner) { summary ->
            if (summary != null) {
                binding.tvNetWorth.text = String.format(Locale.getDefault(), "¥ %.2f", summary.netWorth)
                binding.tvTotalAssets.text = String.format(Locale.getDefault(), "¥ %.2f", summary.totalAssets)
                binding.tvTotalLiabilities.text = String.format(Locale.getDefault(), "¥ %.2f", summary.totalLiabilities)
            }
        }

        viewModel.assetAccounts.observe(viewLifecycleOwner) { list ->
            assetAdapter.submitList(list)
        }

        viewModel.liabilityAccounts.observe(viewLifecycleOwner) { list ->
            liabilityAdapter.submitList(list)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
