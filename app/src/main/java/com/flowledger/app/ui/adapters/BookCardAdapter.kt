package com.flowledger.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flowledger.app.data.model.BookWithStats
import com.flowledger.app.databinding.ItemBookCardBinding

class BookCardAdapter(
    private val onSwitchClick: (BookWithStats) -> Unit,
    private val onExportClick: (BookWithStats) -> Unit,
    private val onDeleteClick: (BookWithStats) -> Unit
) : ListAdapter<BookWithStats, BookCardAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBookCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookWithStats) {
            val book = item.book
            binding.tvBookName.text = book.name
            binding.tvBookStats.text = "${item.accountCount} 个账户 · ${item.transactionCount} 笔流水"

            try {
                binding.viewBookColor.setBackgroundColor(Color.parseColor(book.colorHex))
            } catch (e: Exception) {
                binding.viewBookColor.setBackgroundColor(Color.BLUE)
            }

            if (item.isCurrent) {
                binding.tvActiveBadge.visibility = View.VISIBLE
                binding.btnSwitchBook.visibility = View.GONE
                binding.btnDeleteBook.visibility = View.GONE
            } else {
                binding.tvActiveBadge.visibility = View.GONE
                binding.btnSwitchBook.visibility = View.VISIBLE
                binding.btnDeleteBook.visibility = View.VISIBLE
            }

            binding.btnSwitchBook.setOnClickListener {
                onSwitchClick(item)
            }

            binding.btnExportBook.setOnClickListener {
                onExportClick(item)
            }

            binding.btnDeleteBook.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BookWithStats>() {
            override fun areItemsTheSame(oldItem: BookWithStats, newItem: BookWithStats): Boolean {
                return oldItem.book.id == newItem.book.id
            }

            override fun areContentsTheSame(oldItem: BookWithStats, newItem: BookWithStats): Boolean {
                return oldItem == newItem
            }
        }
    }
}
