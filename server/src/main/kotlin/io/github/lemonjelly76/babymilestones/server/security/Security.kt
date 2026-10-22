package io.github.lemonjelly76.babymilestones.server.security

import io.github.lemonjelly76.babymilestones.server.store.FamilyStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/** The signed-in carer. Every read and write is scoped to [familyId]. */
data class CarerPrincipal(val carerId: String, val familyId: String, val name: String)

object Tokens {
    private val random = SecureRandom()

    fun generate(bytes: Int = 32): String {
        val buffer = ByteArray(bytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    /** Invite codes are short enough to read out: 10 characters without look-alikes. */
    fun inviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}

class BearerTokenFilter(private val families: FamilyStore, private val clock: Clock) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
            val carer = families.carerForToken(Tokens.hash(header.substring(7).trim()), clock.instant())
            if (carer != null) {
                val principal = CarerPrincipal(carer.id, carer.familyId, carer.name)
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_CARER")))
            }
        }
        chain.doFilter(request, response)
    }
}

@Configuration
class SecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity, families: FamilyStore, clock: Clock): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
        .addFilterBefore(BearerTokenFilter(families, clock), UsernamePasswordAuthenticationFilter::class.java)
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, "/api/families", "/api/join").permitAll()
            it.requestMatchers("/actuator/health", "/error").permitAll()
            it.anyRequest().authenticated()
        }
        .build()
}

fun currentCarer(): CarerPrincipal =
    SecurityContextHolder.getContext().authentication?.principal as? CarerPrincipal
        ?: throw IllegalStateException("No signed-in carer")
