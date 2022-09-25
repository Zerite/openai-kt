package dev.zerite.openai.util

import java.util.Base64
import kotlin.random.Random

/**
 * Create a random string of the specified length.
 *
 * @param length The length of the string.
 */
fun Random.nextString(length: Int = 43): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_~."
    return buildString {
        repeat(length) {
            append(chars.random(this@nextString))
        }
    }
}

/**
 * Encode a string to base64.
 */
fun String.toBase64(): String = Base64.getEncoder().encodeToString(this.toByteArray())