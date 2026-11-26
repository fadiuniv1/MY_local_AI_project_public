package com.example.mylocalai.data.remote

import com.example.mylocalai.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.MINUTES)
            // A timed out command may already have executed remotely; never replay POSTs.
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "MYlocalAI/${BuildConfig.VERSION_NAME} (Android)")
                        .apply {
                            if (BuildConfig.BRIDGE_TOKEN.isNotBlank()) {
                                header("X-Bridge-Token", BuildConfig.BRIDGE_TOKEN)
                            }
                        }
                        .build()
                )
            }
            .build()
    }

    val apiService: ApiService by lazy { createApiService(BuildConfig.REMOTE_API_BASE_URL) }

    fun createApiService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
