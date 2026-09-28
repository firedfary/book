package com.flowledger.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowledger.app.FlowLedgerApplication
import com.flowledger.app.R
import com.flowledger.app.data.model.BookWithStats
import com.flowledger.app.databinding.BottomSheetLedgerManagerBinding
import com.flowledger.app.ui.adapters.BookCardAdapter
import com.flowledger.app.ui.viewmodel.MainViewModel
import com.flowledger.app.ui.viewmodel.MainViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LedgerManagerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLedgerManagerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels {
        val app = requireActivity().application as FlowLedgerApplication
        MainViewModelFactory(app.repository)
    }

    private lateinit var adapter: BookCardAdapter

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            readAndImportFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLedgerManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = BookCardAdapter(
            onSwitchClick = { item ->
                viewModel.switchBook(item.book.id)
                Toast.makeText(requireContext(), "已切换至【${item.book.name}】", Toast.LENGTH_SHORT).show()
                dismiss()
            },
            onExportClick = { item ->
                handleExportBook(item)
            },
            onDeleteClick = { item ->
                handleDeleteBook(item)
            }
        )
        binding.rvBooks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBooks.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnCreateBook.setOnClickListener {
            showCreateBookDialog()
        }

        binding.btnImportBook.setOnClickListener {
            showImportOptionsDialog()
        }
    }

    private fun observeData() {
        viewModel.allBooksWithStats.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvBookCount.text = "共 ${list.size} 个账本"
        }
    }

    private fun showCreateBookDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val etName = EditText(context).apply {
            hint = "账本名称 (例如: 家庭账本、旅行账本)"
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_hint))
        }
        val etDesc = EditText(context).apply {
            hint = "描述备注 (可选)"
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_hint))
        }

        layout.addView(etName)
        layout.addView(etDesc)

        MaterialAlertDialogBuilder(context)
            .setTitle("新建账本")
            .setView(layout)
            .setPositiveButton("创建") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(context, "账本名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.createBook(
                    name = name,
                    description = desc,
                    currency = "CNY",
                    colorHex = "#1976D2",
                    onSuccess = { created ->
                        Toast.makeText(context, "成功创建并切换至【${created.name}】", Toast.LENGTH_SHORT).show()
                        dismiss()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleDeleteBook(item: BookWithStats) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除账本")
            .setMessage("确定删除账本【${item.book.name}】吗？\n删除后该账本下的所有账户及交易记录将一并删除，且不可撤销。")
            .setPositiveButton("确认删除") { _, _ ->
                viewModel.deleteBook(
                    item.book.id,
                    onSuccess = {
                        Toast.makeText(requireContext(), "已成功删除账本【${item.book.name}】", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleExportBook(item: BookWithStats) {
        viewModel.exportBook(
            item.book.id,
            onSuccess = { jsonContent ->
                saveAndShareExportedJson(item.book.name, jsonContent)
            },
            onError = { err ->
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun saveAndShareExportedJson(bookName: String, jsonContent: String) {
        try {
            val exportDir = File(requireContext().getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "FlowLedger_${bookName}_$timestamp.json")
            FileOutputStream(file).use { fos ->
                fos.write(jsonContent.toByteArray())
            }

            // 同时复制到剪贴板，方便无外部文件查看器时直接粘贴使用
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FlowLedger_Export", jsonContent))

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "FlowLedger账本导出备份 - $bookName")
                putExtra(Intent.EXTRA_TEXT, jsonContent)
            }

            startActivity(Intent.createChooser(shareIntent, "分享导出账本文件"))
            Toast.makeText(requireContext(), "账本已导出并已复制到剪贴板！\n路径: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "导出异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showImportOptionsDialog() {
        val options = arrayOf("粘贴 JSON 文本导入", "从系统文件选择导入 (*.json)")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择导入方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPasteJsonDialog()
                    1 -> openDocumentLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun showPasteJsonDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            hint = "在此粘贴导出的 FlowLedger 账本 JSON 内容"
            minLines = 5
            maxLines = 10
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_hint))
        }

        // 尝试从剪贴板自动粘贴
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clipText.contains("FlowLedger")) {
                input.setText(clipText)
            }
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
            addView(input)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("粘贴 JSON 导入账本")
            .setView(layout)
            .setPositiveButton("立即导入") { _, _ ->
                val json = input.text.toString().trim()
                if (json.isBlank()) {
                    Toast.makeText(context, "导入内容不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                executeImport(json)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun readAndImportFromUri(uri: Uri) {
        try {
            val content = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: ""
            executeImport(content)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "读取导入文件失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun executeImport(jsonContent: String) {
        viewModel.importBook(
            jsonContent,
            onSuccess = { importedBook ->
                Toast.makeText(requireContext(), "成功导入并切换至【${importedBook.name}】！", Toast.LENGTH_LONG).show()
                dismiss()
            },
            onError = { err ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("导入失败")
                    .setMessage(err)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
