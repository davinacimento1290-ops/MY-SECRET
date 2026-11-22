package com.mysecret.app.data.repository

import com.mysecret.app.data.local.*
import kotlinx.coroutines.flow.Flow

class PrivateRepository(private val dao: PrivateDao) {
    val posts: Flow<List<PostEntity>> = dao.observePosts()
    val vault: Flow<List<VaultItemEntity>> = dao.observeVault()
    val albums: Flow<List<AlbumEntity>> = dao.observeAlbums()
    val media: Flow<List<MediaEntity>> = dao.observeMedia()
    val favorites: Flow<List<PostEntity>> = dao.observeFavorites()

    fun search(query: String): Flow<List<PostEntity>> = dao.searchPosts(query.trim())
    suspend fun addPost(title: String, body: String, category: String, caption: String = "", attachmentUris: String = "") {
        require(title.isNotBlank() || body.isNotBlank()) { "Escreva um título ou conteúdo." }
        dao.savePost(PostEntity(title=title.ifBlank { "Sem título" },body=body,category=category,caption=caption,attachmentUris=attachmentUris))
    }
    suspend fun addVaultItem(title: String, content: String, kind: String) {
        require(title.isNotBlank() && content.isNotBlank()) { "Preencha título e conteúdo." }
        dao.saveVaultItem(VaultItemEntity(title=title,content=content,kind=kind))
    }
    suspend fun toggleFavorite(post: PostEntity) = dao.toggleFavorite(post.id)
    suspend fun deletePost(post: PostEntity) = dao.deletePost(post)
    suspend fun deleteVault(item: VaultItemEntity) = dao.deleteVaultItem(item)
    suspend fun addMedia(uri: String, displayName: String, mimeType: String) {
        require(uri.isNotBlank()) { "Arquivo inválido." }
        dao.saveMedia(MediaEntity(uri=uri,displayName=displayName,mimeType=mimeType))
    }
    suspend fun deleteMedia(item: MediaEntity) = dao.deleteMedia(item)
    suspend fun addAlbum(name: String, description: String) {
        require(name.isNotBlank()) { "Informe o nome do álbum." }
        dao.saveAlbum(AlbumEntity(name=name,description=description))
    }
}
