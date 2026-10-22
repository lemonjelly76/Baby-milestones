package io.github.lemonjelly76.babymilestones.server.store

import io.github.lemonjelly76.babymilestones.core.json.Timestamp
import kotlinx.serialization.Serializable
import java.security.SecureRandom

@Serializable
data class Family(val id: String, val name: String, val region: String)

@Serializable
data class Carer(val id: String, val familyId: String, val name: String, val email: String? = null, val role: String)

@Serializable
data class Photo(val id: String, val familyId: String, val contentType: String, val createdAt: Timestamp)

/** Ids are short, random and typed by prefix (b_ baby, l_ log, …), so they are safe to show and never guessable. */
object Ids {
    private val random = SecureRandom()
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    fun new(prefix: String, length: Int = 16): String =
        prefix + "_" + (1..length).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}

class NotFoundException(message: String) : RuntimeException(message)
