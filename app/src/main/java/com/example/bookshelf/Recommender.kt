package com.example.bookshelf

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Recommender {
    private val emulatorIP = "10.0.2.2"
    private val localIP = "10.100.102.26"
    private val localhost = "localhost"
    private val serverUrls = arrayOf("http://$localhost:5000/recommend")

    private fun parseResponse(jsonString: String): List<Book> {
        val gson = Gson()
        val bookListType = object : TypeToken<List<Book>>() {}.type
        return gson.fromJson(jsonString, bookListType)
    }

    suspend fun recommendBooksFromImage(imageFile: File?): List<Book> {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .readTimeout(10, TimeUnit.MINUTES)
                    .build()

                if (imageFile == null) {
                    return@withContext emptyList()
                }

                val requestBody = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull()).let {
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "image",
                            imageFile.name,
                            it
                        )
                        .build()
                }

                for (url in serverUrls) {
                    val request = requestBody.let {
                        Request.Builder()
                            .url(url)
                            .post(it)
                            .build()
                    }

                    try {
                        val response = request.let { client.newCall(it).execute() }
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            val parsedResponse = responseBody?.let { parseResponse(it) }
                            if (parsedResponse != null) {
                                if(parsedResponse.isNotEmpty()){
                                    Log.i("Recommender","Recommend Successful: $responseBody")
                                    return@withContext parsedResponse
                                }
                            }
                            return@withContext emptyList()
                        } else {
                            Log.e("Recommender","Recommend Failed: ${response.code}")
                            return@withContext emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e("Recommender", "Error calling recommendation server", e)
                        e.printStackTrace()
                        return@withContext emptyList()
                    }
                }
                emptyList()
            } catch (e: Exception) {
                Log.e("Recommender", "Error in Recommender", e)
                e.printStackTrace()
                return@withContext emptyList()
            }
        }
        return emptyList()
    }
}