package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.annotation.KNCallback
import com.tencent.tmm.knoi.annotation.ServiceProvider
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.type.JSValue

@ServiceProvider(singleton = true)
open class MultiCallbackSample {

    private var listener: MultiCallbackListener? = null
    fun init(value: JSValue) {
        info("knoi-sample MultiCallbackSample init")
        listener = value.asMultiCallbackListener()
    }

    fun callListener() {
        info("callListener MultiCallbackSample callListener listener != null ? ${listener != null} ")
        listener?.apply {
            onStart("this is url", "this is params")
            onSuccess(200, "this is response")
            onFail(404)
            onEnd("this is url", "this is params")
        }
    }

}

@KNCallback
interface MultiCallbackListener {
    fun onSuccess(code: Int, response: String)
    fun onFail(code: Int)
    fun onStart(url: String, params: String)
    fun onEnd(url: String, params: String)
}

@KNCallback
interface MultiCallbackListener2 {
    fun onSuccess(code: Int, response: String)
    fun onFail(code: Int)
    fun onStart(url: String, params: String)
    fun onEnd(url: String, params: String)
}