package com.mysecret.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="media_items")
data class MediaEntity(
    @PrimaryKey(autoGenerate=true) val id:Long=0,
    val uri:String,
    val displayName:String,
    val mimeType:String,
    val albumId:Long?=null,
    val addedAt:Long=System.currentTimeMillis()
)
