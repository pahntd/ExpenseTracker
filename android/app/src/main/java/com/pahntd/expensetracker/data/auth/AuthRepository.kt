package com.pahntd.expensetracker.data.auth

import com.pahntd.expensetracker.data.remote.dto.RegisterRequest
import com.pahntd.expensetracker.data.remote.api.AuthApi
import com.pahntd.expensetracker.data.remote.dto.LoginRequest
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi
) {

    suspend fun login(email: String, password: String): LoginResult {
        return try {
            val response = authApi.login(
                LoginRequest(email = email, password = password)
            )
            LoginResult.Success(response)
        } catch (e: HttpException) {
            // The running Ktor backend maps invalid credentials to 400 (IllegalArgumentException
            // -> StatusPages). 401 is also accepted here in case the contract tightens later.
            if (e.code() == 400 || e.code() == 401) {
                LoginResult.InvalidCredentials
            } else {
                LoginResult.UnknownError
            }
        } catch (e: IOException) {
            LoginResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoginResult.UnknownError
        }
    }

    suspend fun register(email: String, password: String): RegisterResult {
        return try {
            val response = authApi.register(
                RegisterRequest(email = email, password = password)
            )
            RegisterResult.Success(response)
        } catch (e: HttpException) {
            // The running Ktor backend maps "Email already exists" to 400 (IllegalArgumentException
            // -> StatusPages). 409 is also accepted here in case the contract tightens later.
            if (e.code() == 400 || e.code() == 409) {
                RegisterResult.EmailAlreadyExists
            } else {
                RegisterResult.UnknownError
            }
        } catch (e: IOException) {
            RegisterResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RegisterResult.UnknownError
        }
    }
}
