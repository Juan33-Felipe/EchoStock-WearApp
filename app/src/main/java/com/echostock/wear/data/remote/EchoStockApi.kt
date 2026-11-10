package com.echostock.wear.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface EchoStockApi {

    @GET("products/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 5): SearchResponseDto

    @GET("products/{sku}")
    suspend fun product(@Path("sku") sku: String): ProductDto

    @GET("products")
    suspend fun products(@Query("page") page: Int, @Query("size") size: Int = 100): PageDto<ProductDto>

    @POST("products")
    suspend fun createProduct(@Body request: CreateProductRequestDto): ProductDto

    @DELETE("products/{sku}")
    suspend fun deleteProduct(@Path("sku") sku: String): Response<Unit>

    /** 201 = nueva, 200 = ya recibida (idempotente por clientAuditId). */
    @POST("audits")
    suspend fun submit(@Body audit: AuditRequestDto): Response<AuditResponseDto>

    @POST("audits/batch")
    suspend fun submitBatch(@Body batch: BatchRequestDto): BatchResponseDto

    @GET("health")
    suspend fun health(): Response<Unit>

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun create(baseUrl: String, apiKey: String, debug: Boolean): EchoStockApi {
            val client = OkHttpClient.Builder()
                // Tiempos cortos: en el almacén es mejor encolar rápido que dejar al operario esperando.
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .writeTimeout(6, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("X-Api-Key", apiKey)
                            .header("Accept", "application/json")
                            .build(),
                    )
                }
                .apply {
                    if (debug) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(EchoStockApi::class.java)
        }
    }
}
