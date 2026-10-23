package io.github.lemonjelly76.babymilestones.app.data

import java.net.URI

/**
 * The family server's address, as typed or shared in an invite. HTTPS works anywhere; plain HTTP only to an address on
 * the home network (or the tailnet, which WireGuard already encrypts), so a family can run the server on WiFi alone.
 */
object ServerAddress {

    sealed interface Check
    data class Valid(val url: String, val plainHttp: Boolean) : Check
    data class Invalid(val message: String) : Check

    fun check(input: String): Check {
        val typed = input.trim()
        if (typed.isEmpty()) return Invalid("Enter your family server's address.")
        val withScheme = if ("://" in typed) typed.trimEnd('/') else {
            val host = host("http://$typed") ?: return Invalid(NOT_AN_ADDRESS)
            (if (isHomeNetwork(host)) "http://" else "https://") + typed.trimEnd('/')
        }
        val host = host(withScheme) ?: return Invalid(NOT_AN_ADDRESS)
        return when (withScheme.substringBefore("://").lowercase()) {
            "https" -> Valid(withScheme, plainHttp = false)
            "http" -> if (isHomeNetwork(host)) Valid(withScheme, plainHttp = true)
            else Invalid("Use https:// for a server outside your home network. Plain http:// only works on your home WiFi.")
            else -> Invalid(NOT_AN_ADDRESS)
        }
    }

    private fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()
        ?.removeSurrounding("[", "]")?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun isHomeNetwork(host: String): Boolean = when {
        ':' in host -> host == "::1" || host.startsWith("fc") || host.startsWith("fd") || LINK_LOCAL_V6.matches(host.substringBefore(':'))
        IPV4.matches(host) -> isPrivateV4(host.split('.').map(String::toInt))
        else -> host == "localhost" || '.' !in host || LOCAL_SUFFIXES.any(host::endsWith)
    }

    private fun isPrivateV4(octets: List<Int>): Boolean {
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // Tailscale's addresses
    }

    private const val NOT_AN_ADDRESS = "That doesn't look like a server address, for example http://192.168.1.20:8083."
    private val IPV4 = Regex("""(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")
    private val LINK_LOCAL_V6 = Regex("fe[89ab][0-9a-f]")
}
