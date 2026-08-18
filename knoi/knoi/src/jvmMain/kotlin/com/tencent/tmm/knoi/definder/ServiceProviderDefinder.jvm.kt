package com.tencent.tmm.knoi.definder

import com.tencent.tmm.knoi.exception.ServiceNotRegisterException

actual inline fun <reified T> notFindService(name: String): T {
    throw ServiceNotRegisterException("not find service $name ${T::class.simpleName}")
}
