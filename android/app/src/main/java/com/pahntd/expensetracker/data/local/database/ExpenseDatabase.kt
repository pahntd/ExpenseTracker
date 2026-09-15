package com.pahntd.expensetracker.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pahntd.expensetracker.data.local.DefaultCategories
import com.pahntd.expensetracker.data.local.converter.SyncStatusConverter
import com.pahntd.expensetracker.data.local.converter.TransactionTypeConverter
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(TransactionTypeConverter::class, SyncStatusConverter::class)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    @Transaction
    open suspend fun resetAllData() {
        transactionDao().deleteAll()
        categoryDao().deleteAll()
        categoryDao().insertAll(
            DefaultCategories.categories
        )
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN serverId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_serverId ON categories(serverId)"
                )
                db.execSQL("ALTER TABLE expenses ADD COLUMN serverId TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_expenses_serverId ON expenses(serverId)"
                )
            }
        }
    }
}