package com.luizgasparetto.backend.monolito.privacy

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ConsentRequest(
    val analytics: Boolean,
    val marketing: Boolean? = false,
    val version: Int = 1
)

data class ConsentView(
    val analytics: Boolean,
    val marketing: Boolean,
    val version: Int,
    val ts: Long
)

@RestController
@RequestMapping("${ApiRoutes.API_V1}/privacy")
class CookieConsentController(
    private val jdbc: NamedParameterJdbcTemplate,
    private val mapper: ObjectMapper,
    private val props: PrivacyProps,            // <— injeta a config tipada
) {

    private fun hmac(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(props.cookieSignSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun setCookie(
        res: HttpServletResponse,
        name: String,
        value: String,
        maxAgeSec: Int,
        httpOnly: Boolean
    ) {
        val parts = mutableListOf<String>()
        parts += "$name=$value"
        parts += "Path=/"
        parts += "Max-Age=$maxAgeSec"
        parts += "SameSite=Lax"
        parts += "Secure"
        if (httpOnly) parts += "HttpOnly"
        res.addHeader("Set-Cookie", parts.joinToString("; "))
    }

    private fun checkOriginOrReferer(req: HttpServletRequest) {
        val origin = req.getHeader("Origin") ?: ""
        val referer = req.getHeader("Referer") ?: ""
        val allowed = listOf(
            "https://www.agenorgasparetto.com.br",
            "https://agenorgasparetto.com.br",
            "http://localhost:5173"
        )
        val ok = allowed.any { a -> origin == a || referer.startsWith(a) }
        if (!ok) throw IllegalStateException("Origin not allowed")
    }

    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun saveConsent(
        @RequestBody @NotNull body: ConsentRequest,
        req: HttpServletRequest,
        res: HttpServletResponse
    ) {
        checkOriginOrReferer(req)

        val payload = mapOf(
            "v" to body.version,
            "a" to if (body.analytics) 1 else 0,
            "m" to if (body.marketing == true) 1 else 0,
            "t" to Instant.now().epochSecond
        )
        val json = mapper.writeValueAsString(payload)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        val sig = hmac(json)

        // 1) Cookies: prefs legível + assinatura HttpOnly
        setCookie(res, "cc_prefs", b64, 365 * 24 * 3600, false)
        setCookie(res, "cc_sig", sig, 365 * 24 * 3600, true)

        // 2) Persistência sem PII (IP com hash + sal)
        val ip = (req.getHeader("X-Forwarded-For") ?: req.remoteAddr ?: "0.0.0.0").split(",").first().trim()
        val ipHash = hmac("$ip|${props.ipSalt}")
        val ua = req.getHeader("User-Agent")?.take(512) ?: "unknown"

        jdbc.update(
            """
                insert into cookie_consents (ip_hash, user_agent, prefs, source)
                values (:ip_hash, :ua, cast(:prefs as jsonb), :src)
            """.trimIndent(),
            mapOf(
                "ip_hash" to ipHash,
                "ua" to ua,
                "prefs" to json,
                "src" to "web"
            )
        )
    }

    @GetMapping("/consent")
    fun getConsent(req: HttpServletRequest): ConsentView? {
        val prefsB64 = req.cookies?.firstOrNull { it.name == "cc_prefs" }?.value ?: return null
        val sig = req.cookies?.firstOrNull { it.name == "cc_sig" }?.value ?: return null
        val json = String(java.util.Base64.getUrlDecoder().decode(prefsB64))
        if (hmac(json) != sig) return null
        val node = mapper.readTree(json)
        return ConsentView(
            analytics = node["a"].asInt(0) == 1,
            marketing = node["m"].asInt(0) == 1,
            version = node["v"].asInt(1),
            ts = node["t"].asLong()
        )
    }
}
