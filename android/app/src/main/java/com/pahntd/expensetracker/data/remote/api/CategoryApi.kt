package com.pahntd.expensetracker.data.remote.api

import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.dto.CreateCategoryRequest
import com.pahntd.expensetracker.data.remote.dto.UpdateCategoryRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface CategoryApi {

    @GET("categories")
    suspend fun getCategories(): List<CategoryResponse>

    @POST("categories")
    suspend fun createCategory(
        @Body request: CreateCategoryRequest
    ): CategoryResponse

    @PUT("categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: String,
        @Body request: UpdateCategoryRequest
    ): CategoryResponse

    @DELETE("categories/{id}")
    suspend fun deleteCategory(
        @Path("id") id: String
    ): Response<Unit>
}
