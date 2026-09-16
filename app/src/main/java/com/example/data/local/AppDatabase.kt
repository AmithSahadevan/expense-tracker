package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.MoneyFlowDao
import com.example.data.local.dao.SavingsDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.dao.UserDao
import com.example.data.local.dao.WishlistDao
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsEntity
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity

@Database(
    entities = [
        UserEntity::class,
        IncomeEntity::class,
        ExpenseEntity::class,
        SavingsEntity::class,
        SavingsTransactionEntity::class,
        MoneyFlowEntity::class,
        WishlistItemEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        CategoryEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao
    abstract fun savingsDao(): SavingsDao
    abstract fun moneyFlowDao(): MoneyFlowDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Explicit migration so existing savings history survives the upgrade
        // (the destructive fallback below would otherwise wipe all user data).
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE savings_transactions ADD COLUMN affectsAvailableMoney INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        // Wishlist product details. Existing items keep their data; "date added" starts as the creation time.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wishlist_items ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishlist_items ADD COLUMN store TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishlist_items ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishlist_items ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wishlist_items ADD COLUMN targetPurchaseDate INTEGER")
                db.execSQL("UPDATE wishlist_items SET dateAdded = createdAt")
            }
        }

        // Money flow records gain an editable date; existing rows keep the day they were created.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE money_flow ADD COLUMN date INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE money_flow SET date = createdAt")
            }
        }

        // Force transition to Indian Rupee for all existing users.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE users SET currencySymbol = '₹'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
