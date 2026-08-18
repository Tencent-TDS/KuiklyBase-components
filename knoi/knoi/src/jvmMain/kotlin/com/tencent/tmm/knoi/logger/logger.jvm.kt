package com.tencent.tmm.knoi.logger

actual fun info(message: String) {
    println("$tag I: $message")
}

actual fun debug(message: String) {
    if (isDebug) {
        println("$tag D: $message")
    }
}

actual fun warming(message: String) {
    System.err.println("$tag W: $message")
}

actual fun e(message: String) {
    System.err.println("$tag E: $message")
}
