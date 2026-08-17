package com.pahntd.expensetracker.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pahntd.expensetracker.utils.IconMapper
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory
import com.pahntd.expensetracker.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onClickItem: (ExpenseWithCategory) -> Unit
) : ListAdapter<ExpenseWithCategory,
        TransactionAdapter.TransactionViewHolder>(DiffCallback) {

    inner class TransactionViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExpenseWithCategory) {
            binding.apply {
                tvCategory.text = item.category.name
                tvDate.text = formatDate(item.expense.date)
                imgCategory.setImageResource(IconMapper.getDrawable(item.category.icon))
                binding.tvTitle.text = item.expense.title
                formatAmount(item, tvAmount)
            }
            binding.root.setOnClickListener {
                onClickItem(item)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun formatAmount(item: ExpenseWithCategory, textView: TextView) {
        val amount = "%,.0f ₫".format(item.expense.amount)
        if (item.expense.type == TransactionType.INCOME) {
            textView.text = "+$amount"
            textView.setTextColor(Color.GREEN)
        } else {
            textView.text = "-$amount"
            textView.setTextColor(Color.RED)
        }
    }

    private fun formatDate(timestamp: Long): String {
        val formatter =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ExpenseWithCategory>() {
            override fun areItemsTheSame(
                oldItem: ExpenseWithCategory,
                newItem: ExpenseWithCategory
            ): Boolean {
                return oldItem.expense.id == newItem.expense.id
            }

            override fun areContentsTheSame(
                oldItem: ExpenseWithCategory,
                newItem: ExpenseWithCategory
            ): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding =
            ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

}