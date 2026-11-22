package com.mysecret.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val caption: String = "",
    val category: String = "Pessoal",
    val attachmentUris: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val kind: String = "Nota",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_transactions")
data class FinanceTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,
    val type: String,
    val category: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "finance_goals")
data class FinanceGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetCents: Long,
    val savedCents: Long = 0,
    val dueAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "finance_bills")
data class FinanceBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,
    val dueAt: Long,
    val paid: Boolean = false,
    val category: String = "Geral"
)
