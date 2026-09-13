package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
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
    fun upgradeFromVersion3KeepsSavingsAndWishlist() = runBlocking {
        createLegacyDatabase(version = 3)
        val database = openWithMigrations()
        try {
            val savings = database.savingsDao().getSavingsTransactionsForUser(1).first().single()
            assertEquals(40_000.0, savings.amount, 0.001)
            assertTrue(savings.affectsAvailableMoney)
            assertLegacyWishlistItemKept(database)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun upgradeFromVersion4KeepsWishlistAndSavingsFlags() = runBlocking {
        createLegacyDatabase(version = 4)
        val database = openWithMigrations()
        try {
            val savings = database.savingsDao().getSavingsTransactionsForUser(1).first().single()
            assertFalse(savings.affectsAvailableMoney)
            assertLegacyWishlistItemKept(database)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    // No destructive fallback here: an invalid migration or schema mismatch makes Room throw.
    private fun openWithMigrations() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
        .build()

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

    /** Writes a database file shaped like app schema [version] (3 or 4), holding one savings record and one wishlist item. */
    private fun createLegacyDatabase(version: Int) {
        context.deleteDatabase(name)
        Room.databaseBuilder(context, AppDatabase::class.java, name).build().apply {
            openHelper.writableDatabase
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // wishlist_items before version 5
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

            if (version == 3) {
                // savings_transactions before version 4 (no affectsAvailableMoney column)
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

    private companion object {
        const val LEGACY_CREATED_AT = 1_757_000_000_000L
    }
}
