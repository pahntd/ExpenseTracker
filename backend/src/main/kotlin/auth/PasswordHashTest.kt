package com.pahntd.expensetracker.auth

fun main(){
    val passwordHasher = BCryptPasswordHasher()

    val password = "123456"

    val hash = passwordHasher.hash(password)

    println("Password: $password")
    println("Hash: $hash")

    println(
        "Correct password: ${
            passwordHasher.verify(password, hash)
        }"
    )

    println(
        "Wrong password: ${
            passwordHasher.verify("wrong-password", hash)
        }"
    )

}