package io.github.lemonjelly76.babymilestones.server.service

import io.github.lemonjelly76.babymilestones.server.security.CarerPrincipal
import io.github.lemonjelly76.babymilestones.server.security.Tokens
import io.github.lemonjelly76.babymilestones.server.store.Carer
import io.github.lemonjelly76.babymilestones.server.store.Family
import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import io.github.lemonjelly76.babymilestones.server.store.Ids
import io.github.lemonjelly76.babymilestones.server.store.NotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class SignedIn(val family: Family, val carer: Carer, val token: String)

data class Invite(val code: String, val expiresAt: Instant)

class ForbiddenException(message: String) : RuntimeException(message)

@Service
class FamilyService(
    private val families: FamilyStore,
    private val clock: Clock,
    @param:Value("\${app.setup-code:}") private val setupCode: String,
) {
    companion object {
        val REGIONS = setOf("england", "scotland", "wales", "northern_ireland")
        val ROLES = setOf("parent", "grandparent", "nanny", "carer")
        val INVITE_LIFETIME: Duration = Duration.ofDays(7)
        const val APP_TOKEN = "app"
    }

    /**
     * Creates a family and its first carer. With SETUP_CODE set, the code must match; without it, only the very
     * first family can be created, so a fresh server can be set up from the phone and then stays closed.
     */
    @Transactional
    fun createFamily(familyName: String, region: String, carerName: String, email: String?, code: String?): SignedIn {
        val allowed = if (setupCode.isNotBlank()) constantTimeEquals(setupCode, code.orEmpty()) else families.familyCount() == 0
        if (!allowed) throw ForbiddenException("A setup code is needed to create a family on this server")
        require(region in REGIONS) { "Region must be one of $REGIONS" }
        val family = Family(Ids.new("f"), familyName.trim().ifEmpty { "Our family" }, region)
        families.createFamily(family)
        return addCarer(family, carerName, email, "parent")
    }

    @Transactional
    fun createInvite(principal: CarerPrincipal): Invite {
        val code = Tokens.inviteCode()
        val expiresAt = clock.instant().plus(INVITE_LIFETIME)
        families.storeInvite(Tokens.hash(code), principal.familyId, principal.carerId, expiresAt)
        return Invite(code, expiresAt)
    }

    @Transactional
    fun join(code: String, name: String, email: String?, role: String): SignedIn {
        val familyId = families.redeemInvite(Tokens.hash(code.trim().uppercase()), clock.instant())
            ?: throw ForbiddenException("That invite code has expired or has already been used")
        val family = families.family(familyId) ?: throw NotFoundException("Family not found")
        return addCarer(family, name, email, role.takeIf { it in ROLES } ?: "carer")
    }

    /** A separate token, e.g. for Claude Desktop's MCP connector, so it can be revoked without signing the phone out. */
    @Transactional
    fun newToken(principal: CarerPrincipal, label: String): String {
        val token = Tokens.generate()
        families.revokeTokens(principal.carerId, label)
        families.storeToken(Tokens.hash(token), principal.carerId, label, null)
        return token
    }

    fun family(principal: CarerPrincipal): Family =
        families.family(principal.familyId) ?: throw NotFoundException("Family not found")

    fun carers(principal: CarerPrincipal): List<Carer> = families.carers(principal.familyId)

    fun carer(principal: CarerPrincipal): Carer = families.carer(principal.carerId) ?: throw NotFoundException("Carer not found")

    @Transactional
    fun updateFamily(principal: CarerPrincipal, name: String?, region: String?): Family {
        val current = family(principal)
        region?.let { require(it in REGIONS) { "Region must be one of $REGIONS" } }
        val updated = current.copy(name = name?.trim()?.ifEmpty { null } ?: current.name, region = region ?: current.region)
        families.updateFamily(updated)
        return updated
    }

    /** Deletes the family and, by cascade, every baby, log, photo, token and note in it. */
    @Transactional
    fun deleteFamily(principal: CarerPrincipal) = families.deleteFamily(principal.familyId)

    private fun addCarer(family: Family, name: String, email: String?, role: String): SignedIn {
        val carer = Carer(Ids.new("c"), family.id, name.trim().ifEmpty { "Parent" }, email?.trim()?.ifEmpty { null }, role)
        families.createCarer(carer)
        val token = Tokens.generate()
        families.storeToken(Tokens.hash(token), carer.id, APP_TOKEN, null)
        return SignedIn(family, carer, token)
    }

    private fun constantTimeEquals(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
