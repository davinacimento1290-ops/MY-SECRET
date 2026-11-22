package com.mysecret.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.mysecret.app.security.DatabaseKeyManager

@Database(
    entities = [
        PostEntity::class, VaultItemEntity::class, AlbumEntity::class, MediaEntity::class,
        FinanceTransactionEntity::class, FinanceGoalEntity::class, FinanceBillEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PrivateDatabase : RoomDatabase() {
    abstract fun privateDao(): PrivateDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS media_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uri TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, albumId INTEGER, addedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS finance_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountCents INTEGER NOT NULL, type TEXT NOT NULL, category TEXT NOT NULL, occurredAt INTEGER NOT NULL, note TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS finance_goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, targetCents INTEGER NOT NULL, savedCents INTEGER NOT NULL, dueAt INTEGER, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS finance_bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, amountCents INTEGER NOT NULL, dueAt INTEGER NOT NULL, paid INTEGER NOT NULL, category TEXT NOT NULL)")
            }
        }

        @Volatile private var INSTANCE: PrivateDatabase? = null

        fun get(context: Context): PrivateDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                System.loadLibrary("sqlcipher")
                val key = DatabaseKeyManager(context.applicationContext).passphrase()
                Room.databaseBuilder(
                    context.applicationContext,
                    PrivateDatabase::class.java,
                    "my_secret_private.db"
                ).openHelperFactory(SupportFactory(key))
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
