package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class DriveFile(
    val id: String = "",
    val name: String = "",
    val mimeType: String = "",
    val size: String? = null,
    val createdTime: String? = null,
    val modifiedTime: String? = null,
    val webContentLink: String? = null,
    val iconLink: String? = null,
    val thumbnailLink: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFilesResponse(
    val files: List<DriveFile>,
    val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFolderRequest(
    val name: String,
    val mimeType: String = "application/vnd.google-apps.folder",
    val parents: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileResponse(
    val id: String?,
    val name: String?,
    val mimeType: String?,
    val kind: String? = null
)

interface DriveApiService {
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType, size, createdTime, modifiedTime, webContentLink, iconLink, thumbnailLink),nextPageToken",
        @Query("key") apiKey: String,
        @Query("pageSize") pageSize: Int = 1000,
        @Query("pageToken") pageToken: String? = null
    ): DriveFilesResponse

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Body folderRequest: DriveFolderRequest,
        @Query("fields") fields: String = "id, name, mimeType",
        @Query("key") apiKey: String
    ): DriveFileResponse
}
