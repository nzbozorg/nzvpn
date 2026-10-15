package nzvpn.nzbozorg.ir.parser

import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import nzvpn.nzbozorg.ir.util.Base64Util
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * تبدیل لینک‌های اشتراک v2ray به مدل Profile.
 *
 * پشتیبانی: vmess (base64 JSON)، vless، trojan، ss (SIP002 و legacy)، socks،
 * http-proxy / https-proxy.
 *
 * این کلاس همان منطق نسخه مرجع JS (tools/xray-config.mjs) است و تست واحد دارد:
 * app/src/test/java/nzvpn/nzbozorg/ir/ShareLinkParserTest.kt
 */
object ShareLinkParser {

    val SUPPORTED_SCHEMES = setOf(
        "vmess", "vless", "trojan", "ss", "socks", "socks5", "http-proxy", "https-proxy",
    )

    /** اگر خط یک لینک قابل‌پشتیبانی نباشد null برمی‌گرداند (ممکن است ساب یا کامنت باشد). */
    fun parse(link: String): Profile? {
        val line = link.trim()
        val scheme = schemeOf(line) ?: return null
        val profile = when (scheme) {
            "vmess" -> parseVmess(line)
            "vless", "trojan" -> parseVlessLike(line)
            "ss" -> parseShadowsocks(line)
            "socks", "socks5" -> parseSocks(line)
            "http-proxy", "https-proxy" -> parseHttpProxy(line)
            else -> return null
        }
        return if (profile.name.isBlank()) profile.copy(name = profile.endpoint) else profile
    }

    fun parseOrThrow(link: String): Profile =
        parse(link) ?: throw IllegalArgumentException("not a supported share link")

    private fun schemeOf(line: String): String? {
        val idx = line.indexOf("://")
        if (idx <= 0) return null
        val scheme = line.substring(0, idx).lowercase()
        return if (scheme in SUPPORTED_SCHEMES) scheme else null
    }

    // ------------------------------------------------------------------ vmess

    /** vmess://BASE64({v,ps,add,port,id,aid,scy,net,type,host,path,tls,sni,alpn,fp}) */
    private fun parseVmess(link: String): Profile {
        val body = link.substring("vmess://".length)
        val decoded = runCatching { Base64Util.decode(body) }
            .getOrNull()
            ?.takeIf { it.trimStart().startsWith("{") }
            ?: percentDecode(body)

        val address = jsonValue(decoded, "add")?.removeSurrounding("[", "]").orEmpty()
        val port = jsonValue(decoded, "port")?.toIntOrNull() ?: 0
        val uuid = jsonValue(decoded, "id").orEmpty()
        require(address.isNotBlank() && port in 1..65535 && uuid.isNotBlank()) {
            "vmess link is missing add/id/port"
        }
        val tls = jsonValue(decoded, "tls").orEmpty().lowercase()
        val security = if (tls == "tls" || tls == "reality") tls else "none"
        val host = jsonValue(decoded, "host").orEmpty()
        return Profile(
            id = sha1(link),
            name = jsonValue(decoded, "ps") ?: jsonValue(decoded, "remarks").orEmpty(),
            protocol = Protocol.VMESS,
            address = address,
            port = port,
            link = link,
            uuid = uuid,
            alterId = jsonValue(decoded, "aid")?.toIntOrNull() ?: 0,
            vmessSecurity = jsonValue(decoded, "scy") ?: "auto",
            network = (jsonValue(decoded, "net") ?: "tcp").lowercase(),
            security = security,
            host = host,
            path = jsonValue(decoded, "path").orEmpty(),
            headerType = (jsonValue(decoded, "type") ?: "none").lowercase(),
            sni = jsonValue(decoded, "sni") ?: if (security != "none") host else "",
            fingerprint = jsonValue(decoded, "fp").orEmpty(),
            alpn = jsonValue(decoded, "alpn")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList(),
        )
    }

    // ------------------------------------------------------- vless / trojan

    /** vless://uuid@host:port?params#name — trojan://password@host:port?params#name */
    private fun parseVlessLike(link: String): Profile {
        val scheme = link.substring(0, link.indexOf("://")).lowercase()
        val isVless = scheme == "vless"
        val tail = splitTail(link.substring(scheme.length + 3))
        val hp = splitHostPort(tail.authority)
        val user = hp.user?.let { percentDecode(it) }
        require(!user.isNullOrBlank()) { "$scheme link has no user part" }

        val q = parseQuery(tail.query)
        fun q1(vararg keys: String): String {
            for (k in keys) q[k.lowercase()]?.let { if (it.isNotEmpty()) return it }
            return ""
        }

        val security = q1("security").lowercase().ifEmpty { "none" }
        val publicKey = q1("pbk")
        require(!(security == "reality" && publicKey.isBlank())) { "reality link has no pbk" }

        return Profile(
            id = sha1(link),
            name = tail.hash,
            protocol = if (isVless) Protocol.VLESS else Protocol.TROJAN,
            address = hp.host,
            port = hp.port,
            link = link,
            uuid = if (isVless) user else "",
            password = if (isVless) "" else user,
            network = q1("type", "net").lowercase().ifEmpty { "tcp" },
            security = security,
            sni = q1("sni").ifEmpty { q1("host") },
            fingerprint = q1("fp"),
            publicKey = publicKey,
            shortId = q1("sid"),
            spiderX = q1("spx"),
            path = q1("path"),
            host = q1("host"),
            serviceName = q1("servicename"),
            mode = q1("mode"),
            headerType = q1("headertype").lowercase().ifEmpty { "none" },
            flow = q1("flow"),
            encryption = q1("encryption").ifEmpty { "none" },
            alpn = q1("alpn").split(',').map { it.trim() }.filter { it.isNotEmpty() },
            allowInsecure = q1("allowinsecure", "allow_insecure", "insecure").let {
                it == "1" || it.equals("true", ignoreCase = true)
            },
        )
    }

    // --------------------------------------------------------------------- ss

    /** ss://method:pass@host:port | ss://BASE64(method:pass@host:port) | ss://BASE64(method:pass)@host:port */
    private fun parseShadowsocks(link: String): Profile {
        val tail = splitTail(link.substring("ss://".length))
        var authority = tail.authority
        val creds: String

        val at = authority.lastIndexOf('@')
        if (at == -1) {
            // کل بخش آتوریتی base64 است: BASE64(method:password@host:port)
            val plain = Base64Util.decode(authority)
            val innerAt = plain.lastIndexOf('@')
            require(innerAt > 0) { "ss link has no method:password" }
            creds = plain.substring(0, innerAt)
            authority = plain.substring(innerAt + 1)
        } else {
            val userinfo = authority.substring(0, at)
            creds = if (userinfo.contains(':')) percentDecode(userinfo) else Base64Util.decode(userinfo)
            authority = authority.substring(at + 1)
        }

        val hp = splitHostPort(authority)
        val sep = creds.indexOf(':')
        require(sep > 0) { "ss link has no method:password" }
        val q = parseQuery(tail.query)
        return Profile(
            id = sha1(link),
            name = tail.hash,
            protocol = Protocol.SS,
            address = hp.host,
            port = hp.port,
            link = link,
            method = creds.substring(0, sep),
            password = creds.substring(sep + 1),
            plugin = q["plugin"].orEmpty(),
        )
    }

    // ------------------------------------------------------------------ socks

    private fun parseSocks(link: String): Profile {
        val scheme = link.substring(0, link.indexOf("://")).lowercase()
        val tail = splitTail(link.substring(scheme.length + 3))
        val hp = splitHostPort(tail.authority)
        val creds = hp.user?.let { if (it.contains(':')) percentDecode(it) else Base64Util.decode(it) }
        val (user, pass) = splitCredentials(creds)
        return Profile(
            id = sha1(link),
            name = tail.hash,
            protocol = Protocol.SOCKS,
            address = hp.host,
            port = hp.port,
            link = link,
            username = user,
            password = pass,
        )
    }

    // ------------------------------------------------------------ http proxy

    private fun parseHttpProxy(link: String): Profile {
        val scheme = link.substring(0, link.indexOf("://")).lowercase()
        val tail = splitTail(link.substring(scheme.length + 3))
        val hp = splitHostPort(tail.authority)
        val creds = hp.user?.let { if (it.contains(':')) percentDecode(it) else Base64Util.decode(it) }
        val (user, pass) = splitCredentials(creds)
        return Profile(
            id = sha1(link),
            name = tail.hash,
            protocol = Protocol.HTTP,
            address = hp.host,
            port = hp.port,
            link = link,
            username = user,
            password = pass,
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun splitCredentials(creds: String?): Pair<String, String> {
        if (creds.isNullOrBlank()) return "" to ""
        val idx = creds.indexOf(':')
        return if (idx == -1) creds to "" else creds.substring(0, idx) to creds.substring(idx + 1)
    }

    private data class HostPort(val user: String?, val host: String, val port: Int)

    private fun splitHostPort(authority: String): HostPort {
        var rest = authority
        var user: String? = null
        val at = rest.lastIndexOf('@')
        if (at != -1) {
            user = rest.substring(0, at)
            rest = rest.substring(at + 1)
        }
        val host: String
        val portStr: String
        if (rest.startsWith("[")) {                    // آدرس IPv6 داخل براکت
            val end = rest.indexOf(']')
            require(end > 0 && end + 2 <= rest.length) { "bad ipv6 host in \"$authority\"" }
            host = rest.substring(1, end)
            portStr = rest.substring(end + 2)
        } else {
            val idx = rest.lastIndexOf(':')
            require(idx > 0) { "missing port in \"$authority\"" }
            host = rest.substring(0, idx)
            portStr = rest.substring(idx + 1)
        }
        val port = portStr.trim().toIntOrNull() ?: throw IllegalArgumentException("bad port in \"$authority\"")
        require(host.isNotBlank() && port in 1..65535) { "invalid host/port in \"$authority\"" }
        return HostPort(user, host, port)
    }

    private data class Tail(val authority: String, val query: String, val hash: String)

    private fun splitTail(rest: String): Tail {
        var r = rest
        var hash = ""
        val h = r.indexOf('#')
        if (h != -1) {
            hash = percentDecode(r.substring(h + 1))
            r = r.substring(0, h)
        }
        var query = ""
        val q = r.indexOf('?')
        if (q != -1) {
            query = r.substring(q + 1)
            r = r.substring(0, q)
        }
        return Tail(r.trimEnd('/'), query, hash)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = HashMap<String, String>()
        for (part in query.split('&')) {
            if (part.isBlank()) continue
            val idx = part.indexOf('=')
            val key = if (idx == -1) part else part.substring(0, idx)
            val value = if (idx == -1) "" else percentDecode(part.substring(idx + 1))
            map[key.lowercase()] = value
        }
        return map
    }

    /** حداقل percent-decode کردن (بدون تبدیل + به فاصله، چون در URIها + می‌تواند واقعی باشد) */
    fun percentDecode(input: String): String {
        if (!input.contains('%')) return input
        val bytes = ByteArrayOutputStream()
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%' && i + 3 <= input.length) {
                val code = input.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    bytes.write(code)
                    i += 3
                    continue
                }
            }
            if (bytes.size() > 0) {
                out.append(String(bytes.toByteArray(), Charsets.UTF_8))
                bytes.reset()
            }
            out.append(c)
            i++
        }
        if (bytes.size() > 0) out.append(String(bytes.toByteArray(), Charsets.UTF_8))
        return out.toString()
    }

    /** استخراج مقدار یک فیلد از JSON، بدون وابستگی به org.json تا در تست JVM هم کار کند. */
    private fun jsonValue(json: String, key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|([^,}\\s]+))")
        val m = regex.find(json) ?: return null
        val unquoted = m.groupValues[2].trim()
        if (unquoted.isNotEmpty()) return unquoted
        val quoted = m.groupValues[1]
        if (quoted.isEmpty()) return null
        return quoted.replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\")
    }

    fun sha1(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
