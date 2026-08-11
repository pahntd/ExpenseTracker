package com.example.expensetracker.ui.category

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensetracker.data.local.IconMapper
import com.example.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.example.expensetracker.databinding.ItemCategoryBinding

class CategoryRecyclerViewAdapter(
    private val onClickItem: (CategoryWithExpenseCount) -> Unit
) :
    ListAdapter<CategoryWithExpenseCount, CategoryRecyclerViewAdapter.CategoryViewHolder>(
        DiffCallback
    ) {

    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: CategoryWithExpenseCount) {
            binding.apply {
                ivIcon.setImageResource(IconMapper.getDrawable(item.icon))
                tvName.text = item.name
                tvExpenseCount.text = item.expenseCount.toString()
            }
            binding.root.setOnClickListener {
                onClickItem(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CategoryWithExpenseCount>() {
            override fun areItemsTheSame(
                oldItem: CategoryWithExpenseCount,
                newItem: CategoryWithExpenseCount
            ): Boolean {
                return oldItem.categoryId == newItem.categoryId
            }

            override fun areContentsTheSame(
                oldItem: CategoryWithExpenseCount,
                newItem: CategoryWithExpenseCount
            ): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding =
            ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}