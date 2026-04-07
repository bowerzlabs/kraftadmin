package com.kraftadmin.utils

import kotlin.test.Test
import kotlin.test.assertEquals
//import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.kraftadmin.utils.*
import kotlinx.serialization.json.Json.Default.encodeToString

internal class PrinterTest {

    @Test
    fun testMessage() {
        val message = "message"
        val testPrinter = Printer(message)
        assertEquals(testPrinter.message, message)
    }

    @Test
    fun testSerialization() {
        val message = "message"
        val json1 = encodeToString(Printer(message))
        val json2 = encodeToString(Printer(message))
        assertEquals(json1, json2)
    }


}