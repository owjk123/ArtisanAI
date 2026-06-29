package com.artisanai.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.artisanai.data.local.GalleryDao
import com.artisanai.data.model.GalleryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

class GalleryRepository(
    private val context: Context,
    private val galleryDao: GalleryDao
) {
    fun getAllImages(): Flow<List<GalleryImage>> = galleryDao.getAllImages()

    fun getInternalDir(): File? = File(context.filesDir, "gallery").also { it.mkdirs() }

    /**
     * 保存Base64图片到内部存储，并插入图库数据库
     */
    suspend fun saveGeneratedImage(
        id: String,
        base64Data: String,
        prompt: String,
        aspectRatio: String,
        imageSize: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val imageBytes = Base64.getDecoder().decode(base64Data)
            val dir = File(context.filesDir, "gallery").also { it.mkdirs() }
            val file = File(dir, "$id.png")

            FileOutputStream(file).use { it.write(imageBytes) }

            val entity = GalleryImage(
                id = id,
                prompt = prompt,
                imagePath = file.absolutePath,
                aspectRatio = aspectRatio,
                imageSize = imageSize,
                createdAt = System.currentTimeMillis()
            )
            galleryDao.insertImage(entity)
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 下载图片到系统相册（从内部文件路径）
     */
    suspend fun saveToAlbum(id: String, imagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(imagePath)
            ?: return@withContext Result.failure(Exception("无法读取图片文件"))
        writeBitmapToAlbum(bitmap).also { if (it.isSuccess) runCatching { galleryDao.markSavedToAlbum(id) } }
    }

    /**
     * 下载图片到系统相册（直接从 base64，不依赖内部文件是否已落盘）。
     * 编辑结果用这个，避免手拼路径出错。
     */
    suspend fun saveToAlbumFromBase64(base64: String): Result<Unit> = withContext(Dispatchers.IO) {
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("图片数据无效"))
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@withContext Result.failure(Exception("无法解码图片"))
        writeBitmapToAlbum(bitmap)
    }

    /** 把 Bitmap 写入系统相册的公共实现（MediaStore / 旧版外存）。 */
    private fun writeBitmapToAlbum(bitmap: Bitmap): Result<Unit> {
        return try {
            val filename = "ArtisanAI_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ArtisanAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return Result.failure(Exception("创建媒体文件失败"))

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return Result.failure(Exception("无法写入相册"))

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val appDir = File(picturesDir, "ArtisanAI").also { it.mkdirs() }
                val file = File(appDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), null, null
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteImage(image: GalleryImage) = withContext(Dispatchers.IO) {
        try {
            File(image.imagePath).delete()
            galleryDao.deleteImage(image)
        } catch (e: Exception) {
            // ignore
        }
    }
}
