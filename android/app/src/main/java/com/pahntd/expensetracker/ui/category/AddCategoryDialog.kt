package com.pahntd.expensetracker.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pahntd.expensetracker.R
import com.pahntd.expensetracker.utils.IconMapper
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.pahntd.expensetracker.databinding.AddCategoryDialogLayoutBinding
import com.pahntd.expensetracker.utils.dp
import kotlinx.coroutines.launch

class AddCategoryDialog : DialogFragment() {
    private var _binding: AddCategoryDialogLayoutBinding? = null
    private val binding
        get() = _binding!!

    private var selectedIcon = "ic_other"
    private val iconList = listOf(
        "ic_food",
        "ic_transport",
        "ic_shopping",
        "ic_salary",
        "ic_entertainment",
        "ic_education",
        "ic_health",
        "ic_other",
        "ic_air_conditioner",
        "ic_bath_outdoor",
        "ic_door",
        "ic_hiking",
        "ic_remote",
        "icon_flower",
        "icon_grass"
    )
    private val iconBackgroundViews = mutableMapOf<String, View>()

    private val categoryViewModel: CategoryViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val categoryId: Long?
        get() = arguments?.getLong(ARG_CATEGORY_ID)?.takeIf { it != 0L }
    private val categoryName: String?
        get() = arguments?.getString(ARG_CATEGORY_NAME)
    private val categoryIcon: String?
        get() = arguments?.getString(ARG_CATEGORY_ICON)

    private val isEditMode: Boolean
        get() = categoryId != null

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = AddCategoryDialogLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isEditMode) {
            loadData()
        }
        setupIcon()
        setupListener()
        observeEvent()
    }

    private fun loadData() {
        categoryName?.let {
            binding.etName.setText(it)
        }
        categoryIcon?.let {
            selectedIcon = it
        }
    }

    private fun setupIcon() {
        iconList.forEach { iconName ->
            val itemView = layoutInflater.inflate(
                R.layout.item_category_icon,
                binding.iconGrid,
                false
            )

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = 56.dp(requireContext())
                columnSpec = GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )
            }

            itemView.layoutParams = params

            val iconContainer = itemView.findViewById<FrameLayout>(R.id.iconContainer)
            val imageView = itemView.findViewById<ImageView>(R.id.ivIcon)
            imageView.setImageResource(
                IconMapper.getDrawable(iconName)
            )
            iconBackgroundViews[iconName] = iconContainer
            iconContainer.isSelected = iconName == selectedIcon
            iconContainer.setOnClickListener {
                selectedIcon = iconName
                updateSelectedIcon()
            }
            binding.iconGrid.addView(itemView)
        }
    }

    private fun updateSelectedIcon() {
        iconBackgroundViews.forEach { (iconName, view) ->
            view.isSelected = iconName == selectedIcon
        }
    }

    private fun setupListener() {
        binding.apply {
            btnCancel.setOnClickListener {
                dismiss()
            }
            btnSave.setOnClickListener {
                saveCategory()
            }
        }
    }

    private fun saveCategory() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            binding.etName.error = "Name is required !"
            return
        }

        if (isEditMode) {
            categoryViewModel.updateCategory(
                CategoryEntity(id = categoryId!!, name = name, icon = selectedIcon)
            )
        } else {
            categoryViewModel.addCategory(
                CategoryEntity(name = name, icon = selectedIcon)
            )
        }
    }

    private fun observeEvent() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                categoryViewModel.eventState.collect { event ->
                    when (event) {
                        CategoryEventState.CategoryAdded -> {
                            dismiss()
                        }

                        is CategoryEventState.Error -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        CategoryEventState.CategoryUpdated -> {
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CATEGORY_ID = "categoryId"
        private const val ARG_CATEGORY_NAME = "categoryName"
        private const val ARG_CATEGORY_ICON = "categoryIcon"

        fun newInstance(
            category: CategoryWithExpenseCount? = null
        ): AddCategoryDialog {
            return AddCategoryDialog().apply {
                if (category != null) {
                    arguments = bundleOf(
                        ARG_CATEGORY_ID to category.categoryId,
                        ARG_CATEGORY_ICON to category.icon,
                        ARG_CATEGORY_NAME to category.name
                    )
                }
            }
        }
    }
}