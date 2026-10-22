package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.ContentVersion
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The content bundle, so phones can pick up newer guidance without an app update. */
@RestController
@RequestMapping("/api")
class ContentController(
    private val content: ContentBundle,
) {
    @GetMapping("/content/version")
    fun contentVersion() = ContentVersion(content.version)

    @GetMapping("/content")
    fun content(): ResponseEntity<ContentBundle> = ResponseEntity.ok().eTag(content.version)
        .cacheControl(CacheControl.noCache()).body(content)

}
