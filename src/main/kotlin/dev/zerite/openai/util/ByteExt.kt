package dev.zerite.openai.util

import java.security.MessageDigest
import java.util.Base64

/**
 * Convert the given byte array to an SHA-256 hash.
 */
fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

/**
 * Convert the given byte array to a base64 string.
 */
fun ByteArray.toBase64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)