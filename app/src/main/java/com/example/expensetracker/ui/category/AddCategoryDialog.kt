package com.example.expensetracker.ui.category

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.expensetracker.R
import com.example.expensetracker.data.local.IconMapper
import com.example.expensetracker.data.local.entity.CategoryEntity
import com.example.expensetracker.databinding.AddCategoryDialogLayoutBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        setupIcon()
        setupListener()
        observeEvent()
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
                height = 56.dp()
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

        categoryViewModel.addCategory(
            CategoryEntity(name = name, icon = selectedIcon)
        )
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
                    }
                }
            }
        }
    }

    private fun Int.dp(): Int {
        return (this * Resources.getSystem().displayMetrics.density).roundToInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}