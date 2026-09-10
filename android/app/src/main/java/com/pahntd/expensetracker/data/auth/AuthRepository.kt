package com.pahntd.expensetracker.data.auth

import com.pahntd.expensetracker.data.remote.api.AuthApi
import com.pahntd.expensetracker.data.remote.dto.LoginRequest
import com.pahntd.expensetracker.data.remote.dto.LoginResponse
import com.pahntd.expensetracker.ui.login.LoginEvent
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
}
