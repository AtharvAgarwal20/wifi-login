package dev.sparshg.bitslogin

import android.net.Network
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * The campus captive portals, spoken over one specific network (the Wi-Fi), so DNS and
 * traffic go through it even while Android still prefers mobile data.
 *
 * Pilani runs a FortiGate: an intercepted plain-HTTP request gets a page that redirects to
 * https://fw.bits-pilani.ac.in:8090/fgtauth?<magic>; the login form there is POSTed back to
 * https://fw.bits-pilani.ac.in:8090/ and a good login answers with a redirect to /keepalive?<token>.
 * Only the firewall's own host is accepted (certificate verified): the redirect comes from
 * whatever network we're on, and any other BITS-hosted server could collect the password.
 */
object Portal {
    const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
    private const val BITS_HOST = """fw\.bits-pilani\.ac\.in""" // ponytail: add Hyderabad's firewall once seen
    private val FORTIGATE = Regex("""https://$BITS_HOST:\d+/fgtauth\?[0-9a-f]+""")
    private val KEEPALIVE = Regex("""https://$BITS_HOST:\d+/keepalive\?[0-9a-f]+""")

    sealed interface State
    data object Online : State
    data class FortiGate(val url: String) : State // the BITS firewall's portal
    data object OtherPortal : State // anything else answering instead of the internet
    data object NoNetwork : State

    sealed interface Outcome
    data object NoForm : Outcome // nothing was sent
    data class Answered(val keepalive: String?) : Outcome // the portal replied to the POST
    data object NoAnswer : Outcome // the POST went out but got no reply

    fun fortiGateUrl(body: String) = FORTIGATE.find(body)?.value
    fun keepaliveUrl(body: String) = KEEPALIVE.find(body)?.value
    fun field(name: String, html: String) =
        Regex("""name="${Regex.escape(name)}"[^>]*value="([^"]*)"""").find(html)?.groupValues?.get(1)

    fun probe(network: Network): State = try {
        val c = open(network, PROBE_URL)
        if (c.responseCode == 204) Online.also { c.disconnect() }
        else fortiGateUrl(c.text())?.let { FortiGate(it) } ?: OtherPortal
    } catch (e: IOException) {
        NoNetwork
    }

    /** One POST per magic. Credentials only go to the BITS host in the URL, certificate verified. */
    fun loginFortiGate(network: Network, portalUrl: String, user: String, pass: String): Outcome {
        val page = try { open(network, portalUrl).text() } catch (e: IOException) { return NoForm }
        val magic = field("magic", page) ?: return NoForm
        val redir = field("4Tredir", page) ?: PROBE_URL
        val origin = portalUrl.substringBefore("/fgtauth") // e.g. https://fw.bits-pilani.ac.in:8090
        return try {
            post(network, "$origin/", "4Tredir" to redir, "magic" to magic, "username" to user, "password" to pass)
                ?.let { Answered(keepaliveUrl(it)) } ?: NoAnswer
        } catch (e: UnknownHostException) {
            NoForm
        }
    }

    /** The Sophos/Cyberoam portal: Goa still runs one (same fields as its web client). */
    fun loginSophos(network: Network, url: String, user: String, pass: String): Outcome = try {
        post(
            network, url, "mode" to "191", "username" to user, "password" to pass,
            "a" to System.currentTimeMillis().toString(), "producttype" to "0"
        )?.let { Answered(null) } ?: NoAnswer
    } catch (e: UnknownHostException) {
        NoForm // not on that campus's network
    }

    fun keepalive(network: Network, url: String): Int {
        require(KEEPALIVE.matches(url)) // never anything else (not /logout)
        return try { open(network, url).run { responseCode.also { disconnect() } } } catch (e: IOException) { -1 }
    }

    private fun post(network: Network, url: String, vararg fields: Pair<String, String>): String? = try {
        val body = fields.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }.toByteArray()
        open(network, url).run {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(body.size) // streamed, so it can't be silently re-sent
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(body) }
            text()
        }
    } catch (e: UnknownHostException) {
        throw e // the host isn't reachable from this network: nothing was sent
    } catch (e: IOException) {
        null
    }

    private fun open(network: Network, url: String) =
        (network.openConnection(URL(url)) as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            useCaches = false
        }

    private fun HttpURLConnection.text(): String = try {
        (if (responseCode >= 400) errorStream else inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
    } finally {
        disconnect()
    }
}
