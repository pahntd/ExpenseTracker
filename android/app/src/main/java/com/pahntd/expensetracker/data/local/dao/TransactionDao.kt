package com.pahntd.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithAmountSummary
import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory
import com.pahntd.expensetracker.data.local.relation.MonthlyAmountSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<TransactionEntity>)

    @Update
    suspend fun update(expense: TransactionEntity)

    @Delete
    suspend fun delete(expense: TransactionEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    /**
     * Reads every transaction row regardless of [TransactionEntity.deletedAt]/
     * [TransactionEntity.syncStatus], for [com.pahntd.expensetracker.data.sync.PullManager]'s
     * merge, which must see local `PENDING_DELETE` tombstones to reconcile them against the server
     * snapshot. UI-facing reads should use [getAll] instead.
     */
    @Query("SELECT * FROM expenses")
    suspend fun findAll(): List<TransactionEntity>

    /**
     * Looks up a transaction regardless of its [TransactionEntity.deletedAt]/
     * [TransactionEntity.syncStatus] state, since mutation flows need to see a pending-delete row
     * to correctly block further edits on it. UI-facing reads should use [getAll] instead.
     */
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: String): TransactionEntity?

    /** Reads rows pending push (create/update/delete) to the server, for [com.pahntd.expensetracker.data.sync.SyncManager]. */
    @Query("SELECT * FROM expenses WHERE syncStatus = :status")
    suspend fun findBySyncStatus(status: SyncStatus): List<TransactionEntity>

    /**
     * Whether any expense row is not [SyncStatus.SYNCED] - i.e. [SyncStatus.PENDING_CREATE],
     * [SyncStatus.PENDING_UPDATE] or [SyncStatus.PENDING_DELETE]. An `EXISTS` query so this never
     * has to load the matching rows themselves.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE syncStatus != :synced)")
    suspend fun hasPendingChanges(synced: SyncStatus = SyncStatus.SYNCED): Boolean

    /**
     * Applies a server response only if the row is still exactly the state
     * ([expectedUpdatedAt]/[expectedSyncStatus]) that was sent to the server, so a response for an
     * in-flight request can never overwrite a newer local mutation that happened while it was
     * running. Returns whether [entity] was applied.
     */
    @Transaction
    suspend fun applyIfUnchanged(
        entity: TransactionEntity,
        expectedUpdatedAt: Long,
        expectedSyncStatus: SyncStatus
    ): Boolean {
        val current = findById(entity.id) ?: return false
        if (current.updatedAt != expectedUpdatedAt || current.syncStatus != expectedSyncStatus) return false
        update(entity)
        return true
    }

    /**
     * Whether any transaction row still references [categoryId], regardless of [TransactionEntity.deletedAt]
     * or [TransactionEntity.syncStatus]. A soft-deleted, still-pending-delete transaction is hidden
     * from normal UI queries but may still exist on the server, so it still counts as a dependency
     * for [com.pahntd.expensetracker.data.sync.SyncManager] deciding whether a category is safe to delete.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE categoryId = :categoryId)")
    suspend fun existsByCategory(categoryId: String): Boolean

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId AND deletedAt IS NULL ORDER BY date DESC")
    fun findByCategory(categoryId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM expenses WHERE type = :type AND deletedAt IS NULL ORDER BY date DESC")
    fun findByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT expenses.* FROM expenses
        INNER JOIN categories
        ON expenses.categoryId = categories.id
        WHERE
        expenses.deletedAt IS NULL
        AND (categories.name LIKE '%' || :keyword || '%'
        OR expenses.title LIKE '%'||:keyword||'%')
        ORDER BY expenses.date DESC
    """
    )
    fun search(keyword: String): Flow<List<ExpenseWithCategory>>

    @Query(
        """
    SELECT * FROM expenses
    WHERE date BETWEEN :startDate AND :endDate
    AND deletedAt IS NULL
    ORDER BY date DESC
"""
    )
    fun getBetweenDate(
        startDate: Long,
        endDate: Long
    ): Flow<List<TransactionEntity>>

    @Query(
        """
    SELECT SUM(amount)
    FROM expenses
    WHERE type = 'INCOME' AND deletedAt IS NULL
"""
    )
    suspend fun getTotalIncome(): Double?

    @Query(
        """
    SELECT SUM(amount)
    FROM expenses
    WHERE type = 'EXPENSE' AND deletedAt IS NULL
"""
    )
    suspend fun getTotalExpense(): Double?

    @Transaction
    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY date DESC")
    fun getAllWithCategory(): Flow<List<ExpenseWithCategory>>


    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id AND deletedAt IS NULL")
    fun getExpenseWithCategoryById(id: String): Flow<ExpenseWithCategory?>

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id AND deletedAt IS NULL")
    suspend fun findExpenseWithCategoryById(id: String): ExpenseWithCategory?

    @Query(
        """
    SELECT
        categories.id AS categoryId,
        categories.name AS categoryName,
        categories.icon AS icon,
        SUM(expenses.amount) AS totalAmount
    FROM expenses
    INNER JOIN categories
        ON expenses.categoryId = categories.id
    WHERE expenses.type = 'EXPENSE' AND expenses.deletedAt IS NULL
    GROUP BY categories.id
    ORDER BY totalAmount DESC
"""
    )
    suspend fun getExpenseByCategory(): List<CategoryWithAmountSummary>

    @Query(
        """
    SELECT
        categories.id AS categoryId,
        categories.name AS categoryName,
        categories.icon AS icon,
        SUM(expenses.amount) AS totalAmount
    FROM expenses
    INNER JOIN categories
        ON expenses.categoryId = categories.id
    WHERE expenses.type = 'INCOME' AND expenses.deletedAt IS NULL
    GROUP BY categories.id
    ORDER BY totalAmount DESC
"""
    )
    suspend fun getIncomeByCategory(): List<CategoryWithAmountSummary>

    /*
     * Date-range counterparts of the statistics queries above, for the Statistics time filter.
     * The range is half-open - [startDate, endDate) in epoch millis - so a transaction exactly at
     * the next period's start belongs to that period only.
     */

    @Query(
        """
    SELECT SUM(amount)
    FROM expenses
    WHERE type = 'INCOME' AND deletedAt IS NULL
    AND date >= :startDate AND date < :endDate
"""
    )
    suspend fun getTotalIncomeInRange(startDate: Long, endDate: Long): Double?

    @Query(
        """
    SELECT SUM(amount)
    FROM expenses
    WHERE type = 'EXPENSE' AND deletedAt IS NULL
    AND date >= :startDate AND date < :endDate
"""
    )
    suspend fun getTotalExpenseInRange(startDate: Long, endDate: Long): Double?

    @Query(
        """
    SELECT
        categories.id AS categoryId,
        categories.name AS categoryName,
        categories.icon AS icon,
        SUM(expenses.amount) AS totalAmount
    FROM expenses
    INNER JOIN categories
        ON expenses.categoryId = categories.id
    WHERE expenses.type = 'EXPENSE' AND expenses.deletedAt IS NULL
    AND expenses.date >= :startDate AND expenses.date < :endDate
    GROUP BY categories.id
    ORDER BY totalAmount DESC
"""
    )
    suspend fun getExpenseByCategoryInRange(startDate: Long, endDate: Long): List<CategoryWithAmountSummary>

    @Query(
        """
    SELECT
        categories.id AS categoryId,
        categories.name AS categoryName,
        categories.icon AS icon,
        SUM(expenses.amount) AS totalAmount
    FROM expenses
    INNER JOIN categories
        ON expenses.categoryId = categories.id
    WHERE expenses.type = 'INCOME' AND expenses.deletedAt IS NULL
    AND expenses.date >= :startDate AND expenses.date < :endDate
    GROUP BY categories.id
    ORDER BY totalAmount DESC
"""
    )
    suspend fun getIncomeByCategoryInRange(startDate: Long, endDate: Long): List<CategoryWithAmountSummary>

    /**
     * Income and expense totals per calendar month within the half-open range
     * [startDate, endDate), oldest month first, for the Statistics monthly trend. `date` is epoch
     * millis, so it is converted to seconds and to the device's local time before taking the year
     * and month; grouping by both keeps e.g. January 2025 and January 2026 apart. Months without
     * any transaction produce no row.
     */
    @Query(
        """
    SELECT
        CAST(strftime('%Y', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
        CAST(strftime('%m', date / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
        SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS totalIncome,
        SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS totalExpense
    FROM expenses
    WHERE deletedAt IS NULL
    AND date >= :startDate AND date < :endDate
    GROUP BY year, month
    ORDER BY year ASC, month ASC
"""
    )
    fun getMonthlyIncomeExpenseInRange(startDate: Long, endDate: Long): Flow<List<MonthlyAmountSummary>>
}
