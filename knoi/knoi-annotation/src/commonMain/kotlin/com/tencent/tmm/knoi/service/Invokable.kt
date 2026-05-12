package com.tencent.tmm.knoi.service

import kotlin.reflect.KClass

interface Invokable {
    fun invoke(name: String, vararg params: Any?): Any?

    fun isRetPromise(method: String): Boolean = false

    fun getReturnType(method: String): KClass<out Any>

    fun getParamsTypeList(method: String): Array<KClass<out Any>>

    fun getMinParamsSize(method: String): Int = getParamsTypeList(method).size
}
