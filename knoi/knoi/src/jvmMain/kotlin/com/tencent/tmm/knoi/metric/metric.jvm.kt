package com.tencent.tmm.knoi.metric

actual fun <T> trace(name: String, block: () -> T) = block()
