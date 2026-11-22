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
    entities = [PostEntity::class, VaultItemEntity::class, AlbumEntity::class, MediaEntity::class],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
