package com.kraftadmin.utils

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.coroutines.*

@Serializable
class Printer(val message: String) {
    fun printMessage() = runBlocking {
        val now: Instant = Clock.System.now()
        launch {
            delay(1000L)
            println(now.toString())
        }
        println(message)
    }
}

//<server>
//<id>${server}</id>
//<username>pM2l1g</username>
//<password>StXqaFngUHEYnxsP5JzWUNTs73Tma84UW</password>
//<username_password>pM2l1g:StXqaFngUHEYnxsP5JzWUNTs73Tma84UW</username_password>
//</server>
