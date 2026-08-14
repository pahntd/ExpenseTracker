package com.example.expensetracker.ui.statistics

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.utils.IconMapper
import com.example.expensetracker.data.local.relation.CategoryWithAmountSummary
import com.example.expensetracker.databinding.ItemStatisticsBinding
import com.example.expensetracker.utils.toCurrency
import java.text.NumberFormat
import java.util.Locale

class CategoryStatisticsAdapter(
    private val type: TransactionType
) :
    ListAdapter<CategoryWithAmountSummary, CategoryStatisticsAdapter.CategoryWithSumViewHolder>(
        DiffCallback
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryWithSumViewHolder {
        val binding =
            ItemStatisticsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent, false
            )
        return CategoryWithSumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryWithSumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }


    inner class CategoryWithSumViewHolder(
        private val binding: ItemStatisticsBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryWithAmountSummary) {
            binding.apply {
                tvCategoryName.text = item.categoryName
                ivIcon.setImageResource(IconMapper.getDrawable(item.icon))
                tvAmount.text = item.totalAmount.toCurrency()
                tvAmount.setTextColor(if (type == TransactionType.EXPENSE) Color.RED else Color.GREEN)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CategoryWithAmountSummary>() {
            override fun areItemsTheSame(
                oldItem: CategoryWithAmountSummary,
                newItem: CategoryWithAmountSummary
            ): Boolean {
                return oldItem.categoryId == newItem.categoryId
            }

            override fun areContentsTheSame(
                oldItem: CategoryWithAmountSummary,
                newItem: CategoryWithAmountSummary
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}