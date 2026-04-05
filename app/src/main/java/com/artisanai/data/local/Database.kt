package com.artisanai.data.local

import androidx.room.*
import com.artisanai.data.model.GalleryImage
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {
    @Query("SELECT * FROM gallery_images ORDER BY createdAt DESC")
    fun getAllImages(): Flow<List<GalleryImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: GalleryImage)

    @Delete
    suspend fun deleteImage(image: GalleryImage)

    @Query("UPDATE gallery_images SET savedToAlbum = 1 WHERE id = :id")
    suspend fun markSavedToAlbum(id: String)

    @Query("DELETE FROM gallery_images WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [GalleryImage::class], version = 1, exportSchema = false)
abstract class ArtisanDatabase : RoomDatabase() {
    abstract fun galleryDao(): GalleryDao

    companion object {
        const val DATABASE_NAME = "artisan_db"
    }
}
