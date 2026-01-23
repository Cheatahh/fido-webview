@file:OptIn(ExperimentalUnsignedTypes::class)

package com.fkmit.fido

import android.util.Base64
import org.json.JSONArray

typealias JsonString = String
typealias UrlString = String

fun String.decodeBase64() = Base64.decode(this, Base64.DEFAULT)!!

fun ByteArray.encodeBase64() = Base64.encodeToString(this, Base64.DEFAULT)!!
fun ByteArray.encodeBase64Url() = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)!!

fun UByteArray.toJsonArray() = JSONArray(this)