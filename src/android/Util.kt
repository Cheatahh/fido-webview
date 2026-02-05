@file:OptIn(ExperimentalUnsignedTypes::class)

package com.fkmit.fido

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import org.json.JSONArray

/**
 * Alias for a string containing JSON data.
 */
typealias JsonString = String

/**
 * Alias for a string containing a URL.
 */
typealias UrlString = String

/**
 * Decode this Base64-encoded string using the default codec.
 * @receiver the Base64 encoded string
 * @return the decoded bytes as a [ByteArray]
 * @throws NullPointerException if decoding fails
 */
fun String.decodeBase64() = Base64.decode(this, Base64.NO_WRAP)!!

/**
 * Encode this byte array to a Base64 string using the default codec.
 * @receiver the bytes to encode
 * @return the Base64 encoded string
 */
fun ByteArray.encodeBase64() = Base64.encodeToString(this, Base64.NO_WRAP)!!

/**
 * Encode this byte array to a URL-safe Base64 string without padding or line wraps (url codec).
 * @receiver the bytes to encode
 * @return the URL-safe Base64 encoded string
 */
fun ByteArray.encodeBase64Url() = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)!!

/**
 * Convert this unsigned byte array to a [JSONArray] of integers.
 * Each `UByte` is converted to an `Int` via [UByte.toInt] before being placed into the array.
 * @receiver the unsigned byte array to convert
 * @return a [JSONArray] containing integer values corresponding to each unsigned byte
 */
fun UByteArray.toJsonArray() = JSONArray(map(UByte::toInt))

/**
 * Lazily log a debug message if the specified tag is loggable at the DEBUG level.
 * */
@SuppressLint("LogTagMismatch")
inline fun log(message: () -> String) {
    if (Log.isLoggable("FIDO", Log.DEBUG))
        Log.d("FIDO", message())
}