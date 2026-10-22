package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.PhotoResponse
import io.github.lemonjelly76.babymilestones.server.security.currentCarer
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import io.github.lemonjelly76.babymilestones.server.store.Photo
import java.time.Clock
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Private photos, stored in the database and served only to the family. */
@RestController
@RequestMapping("/api")
class PhotoController(
    private val familyStore: FamilyStore,
    private val clock: Clock,
) {
    companion object {
        const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
        val PHOTO_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic")
    }

    @PostMapping("/photos", consumes = ["image/*"])
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun uploadPhoto(@RequestHeader("Content-Type") contentType: String, @RequestBody bytes: ByteArray): PhotoResponse {
        val type = contentType.substringBefore(';').trim().lowercase()
        require(type in PHOTO_TYPES) { "Photos must be JPEG, PNG, WebP or HEIC" }
        require(bytes.size in 1..MAX_PHOTO_BYTES) { "Photos must be under 8 MB" }
        val photo = Photo(Ids.new("ph", 20), currentCarer().familyId, type, clock.instant())
        familyStore.storePhoto(photo, bytes)
        return PhotoResponse(photo.id)
    }

    @GetMapping("/photos/{photoId}")
    fun photo(@PathVariable photoId: String): ResponseEntity<ByteArray> {
        val (type, bytes) = familyStore.photo(currentCarer().familyId, photoId) ?: throw NotFoundException("No photo $photoId")
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).cacheControl(CacheControl.noStore()).body(bytes)
    }

}
