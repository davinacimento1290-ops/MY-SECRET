package com.mysecret.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivateDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun observePosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchPosts(query: String): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePost(post: PostEntity): Long

    @Delete
    suspend fun deletePost(post: PostEntity)

    @Query("UPDATE posts SET favorite = NOT favorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)

    @Query("SELECT * FROM posts WHERE favorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<PostEntity>>

    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    fun observeVault(): Flow<List<VaultItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVaultItem(item: VaultItemEntity): Long

    @Delete
    suspend fun deleteVaultItem(item: VaultItemEntity)

    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlbum(album: AlbumEntity): Long

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)

    @Query("SELECT * FROM media_items ORDER BY addedAt DESC")
    fun observeMedia(): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMedia(item: MediaEntity): Long

    @Delete
    suspend fun deleteMedia(item: MediaEntity)

    @Query("SELECT * FROM media_items ORDER BY addedAt ASC")
    suspend fun allMedia(): List<MediaEntity>

    @Query("DELETE FROM media_items")
    suspend fun clearMedia()

    @Query("SELECT * FROM posts ORDER BY createdAt ASC")
    suspend fun allPosts(): List<PostEntity>

    @Query("SELECT * FROM vault_items ORDER BY createdAt ASC")
    suspend fun allVaultItems(): List<VaultItemEntity>

    @Query("SELECT * FROM albums ORDER BY createdAt ASC")
    suspend fun allAlbums(): List<AlbumEntity>

    @Query("DELETE FROM posts")
    suspend fun clearPosts()

    @Query("DELETE FROM vault_items")
    suspend fun clearVault()

    @Query("DELETE FROM albums")
    suspend fun clearAlbums()
}
