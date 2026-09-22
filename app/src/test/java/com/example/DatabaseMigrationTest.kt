package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.BudgetScope
import com.example.data.local.entities.GoalContributionEntity
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsGoalEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Test
    fun upgradeFromVersion3KeepsEveryFeaturesData() = runBlocking {
        withLegacyDatabase(version = 3) { database ->
            val savings = database.savingsDao().getSavingsTransactionsForUser(1).first().single()
            assertEquals(40_000.0, savings.amount, 0.001)
            assertTrue("records from before the flag existed keep affecting available money", savings.affectsAvailableMoney)
            assertLegacyWishlistItemKept(database)
            assertLegacyMoneyFlowKept(database)
            assertLegacyBudgetKept(database)
            assertGoalLedgerIsReady(database)
        }
    }

    @Test
    fun upgradeFromVersion4KeepsSavingsFlags() = runBlocking {
        withLegacyDatabase(version = 4) { database ->
            val savings = database.savingsDao().getSavingsTransactionsForUser(1).first().single()
            assertFalse(savings.affectsAvailableMoney)
            assertLegacyWishlistItemKept(database)
            assertLegacyMoneyFlowKept(database)
        }
    }

    @Test
    fun upgradeFromVersion5KeepsMoneyFlowRecords() = runBlocking {
        withLegacyDatabase(version = 5) { database ->
            assertLegacyMoneyFlowKept(database)
            assertLegacyWishlistItemKept(database)
            assertLegacyBudgetKept(database)
        }
    }

    @Test
    fun upgradeFromVersion6ForcesTheRupeeOnExistingUsers() = runBlocking {
        withLegacyDatabase(version = 6) { database ->
            assertEquals("\u20B9", database.userDao().findUserById(1)?.currencySymbol)
            assertLegacyBudgetKept(database)
        }
    }

    @Test
    fun upgradeFromVersion8KeepsBudgetsAndOpensTheGoalLedger() = runBlocking {
        withLegacyDatabase(version = 8) { database ->
            assertLegacyBudgetKept(database)
            assertGoalLedgerIsReady(database)
            assertLegacyMoneyFlowKept(database)
            assertLegacyWishlistItemKept(database)
        }
    }

    private suspend fun assertLegacyBudgetKept(database: AppDatabase) {
        val budget = database.budgetDao().getBudgetsForUser(1).first().single()
        assertEquals("Food", budget.category)
        assertEquals(6_000.0, budget.allocatedAmount, 0.001)
        // Envelopes from before scopes existed are category envelopes, not the overall one.
        assertEquals(BudgetScope.CATEGORY, budget.scope)
    }

    private suspend fun assertGoalLedgerIsReady(database: AppDatabase) {
        val goalId = database.savingsDao().insertSavingsGoal(
            SavingsGoalEntity(userId = 1, title = "GPU", goalAmount = 60_000.0)
        )
        database.savingsDao().insertGoalContribution(
            GoalContributionEntity(
                userId = 1,
                goalId = goalId,
                transactionType = SavingsActionType.DEPOSIT,
                amount = 5_000.0
            )
        )
        val contribution = database.savingsDao().getGoalContributionsForUser(1).first().single()
        assertEquals(goalId, contribution.goalId)
        assertEquals(5_000.0, contribution.amount, 0.001)
    }

    private suspend fun assertLegacyWishlistItemKept(database: AppDatabase) {
        val item = database.wishlistDao().getWishlistForUser(1).first().single()
        assertEquals("Laptop", item.title)
        assertEquals(28_000.0, item.estimatedCost, 0.001)
        assertEquals("HIGH", item.priority)
        assertEquals("https://shop.example.com/laptop", item.url)
        assertEquals("for work", item.notes)
        assertEquals(LEGACY_CREATED_AT, item.dateAdded)
        assertEquals("", item.imageUrl)
        assertEquals("", item.store)
        assertEquals("", item.description)
        assertNull(item.targetPurchaseDate)
    }

    private suspend fun assertLegacyMoneyFlowKept(database: AppDatabase) {
        val flow = database.moneyFlowDao().getMoneyFlowsForUser(1).first().single()
        assertEquals("Sarah", flow.personName)
        assertEquals("OWED_TO_ME", flow.direction)
        assertEquals(5_000.0, flow.amount, 0.001)
        assertEquals("lunch money", flow.notes)
        assertFalse(flow.isSettled)
        // Records from before the editable date fall back to the day they were created.
        assertEquals(LEGACY_CREATED_AT, flow.date)
    }

    /**
     * Builds a database file shaped like app schema [version], upgrades it through the real migrations,
     * and hands the opened database to [assertions]. No destructive fallback: a bad migration throws.
     */
    private inline fun withLegacyDatabase(version: Int, assertions: (AppDatabase) -> Unit) {
        createLegacyDatabase(version)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9
            )
            .build()
        try {
            assertions(database)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    /** Writes a database file in the shape of app schema [version] (3-8), holding one record per feature. */
    fun createLegacyDatabase(version: Int) {
        context.deleteDatabase(name)
        Room.databaseBuilder(context, AppDatabase::class.java, name).build().apply {
            openHelper.writableDatabase
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            if (version < 6) {
                // money_flow had no editable `date` column before version 6.
                db.execSQL("DROP TABLE money_flow")
                db.execSQL(
                    "CREATE TABLE money_flow (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, " +
                        "personName TEXT NOT NULL, direction TEXT NOT NULL, amount REAL NOT NULL, dueDate INTEGER, " +
                        "isSettled INTEGER NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX index_money_flow_userId ON money_flow (userId)")
                db.execSQL(
                    "INSERT INTO money_flow (userId, personName, direction, amount, dueDate, isSettled, notes, createdAt) " +
                        "VALUES (1, 'Sarah', 'OWED_TO_ME', 5000.0, NULL, 0, 'lunch money', $LEGACY_CREATED_AT)"
                )
            } else {
                db.execSQL(
                    "INSERT INTO money_flow (userId, personName, direction, amount, date, dueDate, isSettled, notes, createdAt) " +
                        "VALUES (1, 'Sarah', 'OWED_TO_ME', 5000.0, $LEGACY_CREATED_AT, NULL, 0, 'lunch money', $LEGACY_CREATED_AT)"
                )
            }

            if (version < 5) {
                // wishlist_items gained product details in version 5.
                db.execSQL("DROP TABLE wishlist_items")
                db.execSQL(
                    "CREATE TABLE wishlist_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, estimatedCost REAL NOT NULL, priority TEXT NOT NULL, url TEXT NOT NULL, " +
                        "notes TEXT NOT NULL, isPurchased INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX index_wishlist_items_userId ON wishlist_items (userId)")
                db.execSQL(
                    "INSERT INTO wishlist_items (userId, title, estimatedCost, priority, url, notes, isPurchased, createdAt) " +
                        "VALUES (1, 'Laptop', 28000.0, 'HIGH', 'https://shop.example.com/laptop', 'for work', 0, $LEGACY_CREATED_AT)"
                )
            } else {
                db.execSQL(
                    "INSERT INTO wishlist_items (userId, title, estimatedCost, priority, url, notes, isPurchased, " +
                        "imageUrl, store, description, dateAdded, targetPurchaseDate, createdAt) " +
                        "VALUES (1, 'Laptop', 28000.0, 'HIGH', 'https://shop.example.com/laptop', 'for work', 0, " +
                        "'', '', '', $LEGACY_CREATED_AT, NULL, $LEGACY_CREATED_AT)"
                )
            }

            if (version < 9) {
                // budgets gained `scope`, and the goal ledger arrived, in version 9.
                db.execSQL("DROP TABLE IF EXISTS goal_contributions")
                db.execSQL("DROP TABLE budgets")
                db.execSQL(
                    "CREATE TABLE budgets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, " +
                        "category TEXT NOT NULL, allocatedAmount REAL NOT NULL, period TEXT NOT NULL, " +
                        "spentAmount REAL NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX index_budgets_userId ON budgets (userId)")
            }
            db.execSQL(
                "INSERT INTO budgets (userId, category, allocatedAmount, period, spentAmount, createdAt) " +
                    "VALUES (1, 'Food', 6000.0, 'MONTHLY', 0.0, $LEGACY_CREATED_AT)"
            )

            if (version < 8) {
                // users gained avatarImagePath in version 8.
                db.execSQL("DROP TABLE users")
                db.execSQL(
                    "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL, " +
                        "email TEXT NOT NULL, displayName TEXT NOT NULL, avatarEmoji TEXT NOT NULL, " +
                        "avatarColorHex TEXT NOT NULL, passwordHash TEXT NOT NULL, currencySymbol TEXT NOT NULL, " +
                        "monthlySalary REAL NOT NULL, paydayDayOfMonth INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX index_users_username ON users (username)")
                db.execSQL("CREATE UNIQUE INDEX index_users_email ON users (email)")
                db.execSQL(
                    "INSERT INTO users (username, email, displayName, avatarEmoji, avatarColorHex, passwordHash, " +
                        "currencySymbol, monthlySalary, paydayDayOfMonth, createdAt) " +
                        "VALUES ('amith', 'a@example.com', 'Amith', '\u26A1', '#0C0F14', '', '$', 0.0, 1, $LEGACY_CREATED_AT)"
                )
            } else {
                db.execSQL(
                    "INSERT INTO users (username, email, displayName, avatarEmoji, avatarColorHex, avatarImagePath, " +
                        "passwordHash, currencySymbol, monthlySalary, paydayDayOfMonth, createdAt) " +
                        "VALUES ('amith', 'a@example.com', 'Amith', '\u26A1', '#0C0F14', NULL, '', '\u20B9', 0.0, 1, $LEGACY_CREATED_AT)"
                )
            }

            if (version < 4) {
                // savings_transactions gained affectsAvailableMoney in version 4.
                db.execSQL("DROP TABLE savings_transactions")
                db.execSQL(
                    "CREATE TABLE savings_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "userId INTEGER NOT NULL, savingsType TEXT NOT NULL, transactionType TEXT NOT NULL, " +
                        "amount REAL NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, date INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX index_savings_transactions_userId ON savings_transactions (userId)")
                db.execSQL("CREATE INDEX index_savings_transactions_savingsType ON savings_transactions (savingsType)")
                db.execSQL(
                    "INSERT INTO savings_transactions (userId, savingsType, transactionType, amount, title, notes, date, createdAt) " +
                        "VALUES (1, 'EMERGENCY_FUND', 'DEPOSIT', 40000.0, 'Cushion', '', 0, 0)"
                )
            } else {
                db.execSQL(
                    "INSERT INTO savings_transactions (userId, savingsType, transactionType, amount, title, notes, date, " +
                        "affectsAvailableMoney, createdAt) VALUES (1, 'EMERGENCY_FUND', 'DEPOSIT', 40000.0, 'Cushion', '', 0, 0, 0)"
                )
            }
            db.version = version
        }
    }

    companion object {
        const val LEGACY_CREATED_AT = 1_757_000_000_000L
    }
}
