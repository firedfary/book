package com.flowledger.app.ui.transactions

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.databinding.FragmentTransactionsBinding
import com.flowledger.app.ocr.parser.BillParserPipeline
import com.flowledger.app.ocr.plugin.OcrPluginManager
import com.flowledger.app.ocr.plugin.OcrTextLine
import com.flowledger.app.ocr.slice.ImageSlicingEngine
import com.flowledger.app.ui.adapters.TransactionListAdapter
import com.flowledger.app.ui.dialogs.BillImportPreviewBottomSheet
import com.flowledger.app.ui.dialogs.SelectTargetAccountBottomSheet
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private lateinit var adapter: TransactionListAdapter
    private var pendingTargetAccount: AccountEntity? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val account = pendingTargetAccount
            if (account != null) {
                processScreenshotImport(uri, account)
            }
        }
    }

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

        // 📷 截图识图导入账单入口
        binding.btnImportScreenshot.setOnClickListener {
            val selectAccountSheet = SelectTargetAccountBottomSheet { chosenAccount ->
                pendingTargetAccount = chosenAccount
                // 唤起相册/文件选择器
                pickImageLauncher.launch("image/*")
            }
            selectAccountSheet.show(parentFragmentManager, "SelectTargetAccountBottomSheet")
        }
    }

    /**
     * 处理所选长截图的切片、识别、管道适配与预览展示
     */
    private fun processScreenshotImport(uri: Uri, targetAccount: AccountEntity) {
        binding.cardOcrProgress.visibility = View.VISIBLE
        binding.tvOcrProgressMsg.text = "正在切片与准备识别..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val slicingEngine = ImageSlicingEngine(requireContext())
                val slices = slicingEngine.sliceImage(uri, sliceHeight = 2800, overlapHeight = 150)

                withContext(Dispatchers.Main) {
                    binding.tvOcrProgressMsg.text = "共切片 ${slices.size} 块，正在执行端侧识别..."
                }

                val pluginManager = OcrPluginManager(requireContext())
                val plugin = pluginManager.getActivePlugin()

                val allLines = mutableListOf<OcrTextLine>()

                for ((idx, slice) in slices.withIndex()) {
                    withContext(Dispatchers.Main) {
                        binding.tvOcrProgressMsg.text = "正在识别切片 ${idx + 1} / ${slices.size}..."
                    }
                    val sliceResult = plugin.processSlice(slice.bitmap, slice.globalOffsetY)
                    allLines.addAll(sliceResult.lines)
                    slice.bitmap.recycle() // 及时释放内存
                }

                withContext(Dispatchers.Main) {
                    binding.tvOcrProgressMsg.text = "正在进行规则解析与对账校验..."
                }

                val accounts = viewModel.allAccountsWithBalances.value?.map { it.account } ?: emptyList()
                val pipeline = BillParserPipeline()
                val parseResult = pipeline.parse(allLines, accounts)

                withContext(Dispatchers.Main) {
                    binding.cardOcrProgress.visibility = View.GONE

                    if (parseResult.items.isEmpty()) {
                        Toast.makeText(requireContext(), "未能从截图中解析出有效账单流水，请确认图片清晰度", Toast.LENGTH_LONG).show()
                    } else {
                        val previewSheet = BillImportPreviewBottomSheet(targetAccount, parseResult)
                        previewSheet.show(parentFragmentManager, "BillImportPreviewBottomSheet")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.cardOcrProgress.visibility = View.GONE
                    Toast.makeText(requireContext(), "识别处理失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
