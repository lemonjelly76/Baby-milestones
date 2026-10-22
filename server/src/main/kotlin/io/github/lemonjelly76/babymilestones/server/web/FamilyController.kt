package io.github.lemonjelly76.babymilestones.server.web

import io.github.lemonjelly76.babymilestones.core.api.ContactRequest
import io.github.lemonjelly76.babymilestones.core.api.CreateFamilyRequest
import io.github.lemonjelly76.babymilestones.core.api.ExportResponse
import io.github.lemonjelly76.babymilestones.core.api.InviteRequest
import io.github.lemonjelly76.babymilestones.core.api.InviteResponse
import io.github.lemonjelly76.babymilestones.core.api.JoinRequest
import io.github.lemonjelly76.babymilestones.core.api.MeResponse
import io.github.lemonjelly76.babymilestones.core.api.SignInResponse
import io.github.lemonjelly76.babymilestones.core.api.TokenRequest
import io.github.lemonjelly76.babymilestones.core.api.TokenResponse
import io.github.lemonjelly76.babymilestones.core.api.UpdateFamilyRequest
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.records.Contact
import io.github.lemonjelly76.babymilestones.server.security.currentCarer
import io.github.lemonjelly76.babymilestones.server.service.BabyService
import io.github.lemonjelly76.babymilestones.server.service.FamilyService
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Families, sign-in, invites, connector tokens, contacts and the full export. */
@RestController
@RequestMapping("/api")
class FamilyController(
    private val familyService: FamilyService,
    private val babyService: BabyService,
    private val content: ContentBundle,
    private val clock: Clock,
    @param:Value("\${app.mcp-public-url:}") private val mcpPublicUrl: String,
) {
    @PostMapping("/families")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFamily(@RequestBody request: CreateFamilyRequest): SignInResponse =
        familyService.createFamily(request.familyName, request.region, request.carerName, request.email, request.setupCode)
            .let { SignInResponse(it.family, it.carer, it.token) }

    @PostMapping("/join")
    fun join(@RequestBody request: JoinRequest): SignInResponse =
        familyService.join(request.code, request.name, request.email, request.role).let { SignInResponse(it.family, it.carer, it.token) }

    @GetMapping("/me")
    fun me(): MeResponse {
        val principal = currentCarer()
        return MeResponse(
            familyService.family(principal), familyService.carer(principal), familyService.carers(principal),
            babyService.babies(principal.familyId), content.version, mcpPublicUrl.ifBlank { null },
            familyService.contacts(principal),
        )
    }

    @PatchMapping("/family")
    fun updateFamily(@RequestBody request: UpdateFamilyRequest) = familyService.updateFamily(currentCarer(), request.name, request.region)

    @DeleteMapping("/family")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFamily() = familyService.deleteFamily(currentCarer())

    @PostMapping("/invites")
    fun invite(@RequestBody(required = false) request: InviteRequest?): InviteResponse =
        familyService.createInvite(currentCarer(), request?.access ?: FamilyService.EDIT).let { InviteResponse(it.code, it.expiresAt) }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    fun addContact(@RequestBody request: ContactRequest): Contact = familyService.saveContact(currentCarer(), null, request.toContact())

    @PutMapping("/contacts/{contactId}")
    fun updateContact(@PathVariable contactId: String, @RequestBody request: ContactRequest): Contact =
        familyService.saveContact(currentCarer(), contactId, request.toContact())

    @DeleteMapping("/contacts/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContact(@PathVariable contactId: String) = familyService.deleteContact(currentCarer(), contactId)

    @PostMapping("/tokens")
    fun token(@RequestBody request: TokenRequest): TokenResponse {
        require(request.label != FamilyService.APP_TOKEN) { "Choose another label" }
        return TokenResponse(familyService.newToken(currentCarer(), request.label), request.label)
    }

    @GetMapping("/export")
    fun export(): ExportResponse {
        val principal = currentCarer()
        return ExportResponse(
            familyService.family(principal), familyService.carers(principal),
            babyService.babies(principal.familyId).map { babyService.data(principal.familyId, it.id) }, clock.instant(),
        )
    }

    private fun ContactRequest.toContact() = Contact("", role, name, phone, email, note)
}
