package com.mysecret.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory
import com.mysecret.app.security.DatabaseKeyManager

@Database(
    entities = [PostEntity::class, VaultItemEntity::class, AlbumEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PrivateDatabase : RoomDatabase() {
    abstract fun privateDao(): PrivateDao

    companion object {
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
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
