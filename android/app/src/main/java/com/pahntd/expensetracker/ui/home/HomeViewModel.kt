package com.pahntd.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkState
import com.pahntd.expensetracker.data.repository.TransactionRepository
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncState
import com.pahntd.expensetracker.data.sync.SyncStatusHolder
import com.pahntd.expensetracker.data.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val syncStatusHolder: SyncStatusHolder,
    private val syncScheduler: SyncScheduler,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<HomeEventState>()
    val eventState = _eventState.asSharedFlow()

    /**
     * Display-only pass-through of [SyncStatusHolder.state] - display text/visibility mapping
     * lives in the UI layer, not here, so this is exposed as-is rather than copied into a second
     * `MutableStateFlow`.
     */
    val syncState: StateFlow<SyncState> = syncStatusHolder.state

    private val searchQuery = MutableStateFlow("")

    init {
        observeSummary()
        observeSearchQuery()
    }

    private fun observeSummary() {
        viewModelScope.launch {
            transactionRepository.getAllExpensesWithCategory().collect { list ->
                val income = list.filter { it.transaction.type == TransactionType.INCOME }
                    .sumOf { it.transaction.amount }
                val expense = list.filter { it.transaction.type == TransactionType.EXPENSE }
                    .sumOf { it.transaction.amount }
                _uiState.update {
                    it.copy(
                        totalIncome = income,
                        totalExpense = expense,
                        balance = income - expense
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQuery.flatMapLatest { keyword ->
                if (keyword.isBlank()) {
                    transactionRepository.getAllExpensesWithCategory()
                } else {
                    transactionRepository.searchExpense(keyword)
                }
            }.collect { list ->
                _uiState.update {
                    it.copy(transactions = list)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    /**
     * Manual sync from Home's sync button. Only requests a pass through [SyncScheduler] (same
     * unique work as every other trigger, so a tap while one is already queued/running is a
     * no-op) - never calls SyncManager directly. Offline, WorkManager holds the request until the
     * network is back; the user is told so, since nothing visible happens until then.
     */
    fun onSyncClick() {
        syncScheduler.enqueueSync(SyncTrigger.MANUAL)
        if (networkMonitor.networkState.value == NetworkState.OFFLINE) {
            viewModelScope.launch {
                _eventState.emit(HomeEventState.SyncQueuedOffline)
            }
        }
    }
}
