package com.example.meshrelaysdk

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface MeshApiService {
    @POST("/api/sos")
    suspend fun uploadBatch(@Body packets: List<SosPacketDto>): Response<Map<String, Int>>
}