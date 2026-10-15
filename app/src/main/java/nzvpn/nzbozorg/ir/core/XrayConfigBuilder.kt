package nzvpn.nzbozorg.ir.core

import nzvpn.nzbozorg.ir.model.Profile
import nzvpn.nzbozorg.ir.model.Protocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * ساخت کانفیگ کامل Xray از یک Profile.
 *
 * ⚠️ این ساختار **عیناً** از قالب رسمی v2rayNG (assets/v2ray_config_with_tun.json)
 * برداشته شده، چون همان چیزی است که هستهٔ libv2ray انتظار دارد:
 *
 *   inbounds:
 *     tun   → {"tag":"tun","protocol":"tun","settings":{"name":"xray0","MTU":1500,"userLevel":8},
 *              "sniffing":{...}}      ← توجه: کلید MTU با حرف بزرگ و بدون gateway/dns
 *     socks → محلی روی 127.0.0.1:10808 (برای تست و ابزارها)
 *   outbounds: proxy (اول = پیش‌فرض) · direct (freedom + sockopt.domainStrategy) · block (blackhole)
 *   routing:   بدون هیچ قاعدهٔ geosite/geoip — چون فایل‌های geo داخل APK نیستند و
 *              ارجاع به آن‌ها باعث می‌شد هسته بالا نیاید (باگ نسخهٔ قبل همین بود).
 *
 * نکته: آدرس/مسیر/DNS تونل از داخل خود VpnService.Builder به کرنل داده می‌شود،
 * نه از این کانفیگ.
 */
object XrayConfigBuilder {

    data class Options(
        val mtu: Int = 1500,
        /** نام اینترفیس TUN (داخل هسته؛ نام واقعی را اندروید می‌سازد) */
        val tunName: String = "xray0",
        val socksPort: Int = 10808,
        val dns: List<String> = listOf("1.1.1.1", "8.8.8.8"),
        val logLevel: String = "warning",
        val userLevel: Int = 8,
        val includeTunInbound: Boolean = true,
        val includeSocksInbound: Boolean = true,
    )

    fun buildJson(profile: Profile, options: Options): String {
        if (profile.protocol == Protocol.CUSTOM) {
            return patchCustomConfig(profile.rawJson.orEmpty(), options)
        }

        val root = JSONObject()
        root.put("stats", JSONObject())
        root.put("log", JSONObject().put("loglevel", options.logLevel))
        root.put("policy", policy(options))

        val inbounds = JSONArray()
        if (options.includeTunInbound) inbounds.put(tunInbound(options))
        if (options.includeSocksInbound) inbounds.put(socksInbound(options))
        root.put("inbounds", inbounds)

        val outbounds = JSONArray()
        outbounds.put(buildOutbound(profile, "proxy"))
        outbounds.put(directOutbound())
        outbounds.put(blockOutbound())
        root.put("outbounds", outbounds)

        root.put("routing", routing())
        root.put(
            "dns",
            JSONObject()
                .put("hosts", JSONObject())
                .put("servers", JSONArray(options.dns))
                .put("queryStrategy", "UseIPv4"),
        )
        return root.toString(2)
    }

    /** کانفیگ کامل و دست‌نخوردهٔ کاربر + تونل و مسیر پیش‌فرض روی اولین outbound */
    fun patchCustomConfig(raw: String, options: Options): String {
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            throw IllegalArgumentException("فایل کانفیگ، یک JSON معتبر نیست")
        }

        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        if (outbounds.length() == 0) {
            throw IllegalArgumentException("کانفیگ هیچ outbound ندارد")
        }
        val firstTag = outbounds.optJSONObject(0)?.optString("tag").orEmpty()

        if (!root.has("policy")) root.put("policy", policy(options))

        val inbounds = root.optJSONArray("inbounds") ?: JSONArray().also { root.put("inbounds", it) }
        val hasTun = (0 until inbounds.length()).any {
            inbounds.optJSONObject(it)?.optString("protocol") == "tun"
        }
        if (!hasTun && options.includeTunInbound) {
            inbounds.put(tunInbound(options))
        }
        val hasSocks = (0 until inbounds.length()).any {
            inbounds.optJSONObject(it)?.optString("protocol") == "socks"
        }
        if (!hasSocks && options.includeSocksInbound) {
            inbounds.put(socksInbound(options))
        }

        // مسیر پیش‌فرض: همه‌چیز از اولین outbound کانفیگ کاربر
        val rules = JSONArray()
        if (firstTag.isNotBlank()) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", firstTag),
            )
        }
        root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
        if (!root.has("stats")) root.put("stats", JSONObject())
        return root.toString(2)
    }

    // ------------------------------------------------------------- اجزای پایه

    private fun policy(options: Options) = JSONObject()
        .put(
            "levels",
            JSONObject().put(
                options.userLevel.toString(),
                JSONObject()
                    .put("handshake", 4)
                    .put("connIdle", 300)
                    .put("uplinkOnly", 1)
                    .put("downlinkOnly", 1),
            ),
        )
        .put(
            "system",
            JSONObject()
                .put("statsInboundUplink", true)
                .put("statsInboundDownlink", true)
                .put("statsOutboundUplink", true)
                .put("statsOutboundDownlink", true),
        )

    private fun sniffing() = JSONObject()
        .put("enabled", true)
        .put("destOverride", JSONArray(listOf("http", "tls", "quic")))

    private fun tunInbound(options: Options) = JSONObject()
        .put("tag", "tun")
        .put("protocol", "tun")
        .put(
            "settings",
            JSONObject()
                .put("name", options.tunName)
                .put("MTU", options.mtu)
                .put("userLevel", options.userLevel),
        )
        .put("sniffing", sniffing())

    private fun socksInbound(options: Options) = JSONObject()
        .put("tag", "socks")
        .put("listen", "127.0.0.1")
        .put("port", options.socksPort)
        .put("protocol", "socks")
        .put(
            "settings",
            JSONObject()
                .put("auth", "noauth")
                .put("udp", true)
                .put("userLevel", options.userLevel),
        )
        .put("sniffing", sniffing())

    private fun directOutbound() = JSONObject()
        .put("tag", "direct")
        .put("protocol", "freedom")
        .put(
            "streamSettings",
            JSONObject().put(
                "sockopt",
                JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put(
                        "happyEyeballs",
                        JSONObject().put("tryDelayMs", 250).put("interleave", 2),
                    ),
            ),
        )

    private fun blockOutbound() = JSONObject()
        .put("tag", "block")
        .put("protocol", "blackhole")
        .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))

    /** همهٔ ترافیک از سرور — بدون هیچ قاعدهٔ وابسته به فایل geo */
    private fun routing() = JSONObject()
        .put("domainStrategy", "AsIs")
        .put(
            "rules",
            JSONArray().put(
                JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", "proxy"),
            ),
        )

    // -------------------------------------------------------------- outbound

    fun buildOutbound(profile: Profile, tag: String = "proxy"): JSONObject {
        val stream = JSONObject()
            .put("network", normalizeNetwork(profile.network))
            .put("security", if (profile.security.isBlank()) "none" else profile.security)

        when (profile.security) {
            "tls" -> {
                val tls = JSONObject()
                val sni = profile.sni.ifBlank { profile.host }
                if (sni.isNotBlank()) tls.put("serverName", sni)
                if (profile.fingerprint.isNotBlank()) tls.put("fingerprint", profile.fingerprint)
                if (profile.alpn.isNotEmpty()) tls.put("alpn", JSONArray(profile.alpn))
                if (profile.allowInsecure) tls.put("allowInsecure", true)
                stream.put("tlsSettings", tls)
            }

            "xtls" -> {
                val tls = JSONObject()
                val sni = profile.sni.ifBlank { profile.host }
                if (sni.isNotBlank()) tls.put("serverName", sni)
                if (profile.alpn.isNotEmpty()) tls.put("alpn", JSONArray(profile.alpn))
                stream.put("xtlsSettings", tls)
            }

            "reality" -> stream.put(
                "realitySettings",
                JSONObject()
                    .put("show", false)
                    .put("serverName", profile.sni.ifBlank { profile.host.ifBlank { profile.address } })
                    .put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                    .put("publicKey", profile.publicKey)
                    .put("shortId", profile.shortId)
                    .put("spiderX", profile.spiderX.ifBlank { "/" }),
            )
        }

        when (stream.getString("network")) {
            "ws" -> {
                val ws = JSONObject().put("path", profile.path.ifBlank { "/" })
                if (profile.host.isNotBlank()) ws.put("host", profile.host)
                stream.put("wsSettings", ws)
            }

            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", profile.serviceName))

            "http", "h2" -> stream.put(
                "httpSettings",
                JSONObject()
                    .put("host", JSONArray(if (profile.host.isBlank()) emptyList() else listOf(profile.host)))
                    .put("path", profile.path.ifBlank { "/" }),
            )

            "xhttp" -> stream.put(
                "xhttpSettings",
                JSONObject()
                    .put("path", profile.path.ifBlank { "/" })
                    .put("host", profile.host)
                    .put("mode", profile.mode.ifBlank { "auto" }),
            )

            "tcp" -> if (profile.headerType == "http") {
                stream.put(
                    "tcpSettings",
                    JSONObject().put(
                        "header",
                        JSONObject()
                            .put("type", "http")
                            .put(
                                "request",
                                JSONObject()
                                    .put("path", JSONArray(listOf(profile.path.ifBlank { "/" })))
                                    .put(
                                        "headers",
                                        JSONObject().put(
                                            "Host",
                                            JSONArray(if (profile.host.isBlank()) emptyList() else listOf(profile.host)),
                                        ),
                                    ),
                            ),
                    ),
                )
            }
        }

        val outbound = JSONObject().put("tag", tag).put("streamSettings", stream)

        when (profile.protocol) {
            Protocol.VMESS -> {
                outbound.put("protocol", "vmess")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.address)
                                .put("port", profile.port)
                                .put(
                                    "users",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("id", profile.uuid)
                                            .put("alterId", profile.alterId)
                                            .put("security", profile.vmessSecurity.ifBlank { "auto" }),
                                    ),
                                ),
                        ),
                    ),
                )
            }

            Protocol.VLESS -> {
                val user = JSONObject()
                    .put("id", profile.uuid)
                    .put("encryption", profile.encryption.ifBlank { "none" })
                if (profile.flow.isNotBlank()) user.put("flow", profile.flow)
                outbound.put("protocol", "vless")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "vnext",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.address)
                                .put("port", profile.port)
                                .put("users", JSONArray().put(user)),
                        ),
                    ),
                )
            }

            Protocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.address)
                                .put("port", profile.port)
                                .put("password", profile.password),
                        ),
                    ),
                )
            }

            Protocol.SS -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put(
                    "settings",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(
                            JSONObject()
                                .put("address", profile.address)
                                .put("port", profile.port)
                                .put("method", profile.method)
                                .put("password", profile.password),
                        ),
                    ),
                )
            }

            Protocol.SOCKS -> {
                val server = JSONObject().put("address", profile.address).put("port", profile.port)
                if (profile.username.isNotBlank()) {
                    server.put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("user", profile.username)
                                .put("pass", profile.password),
                        ),
                    )
                }
                outbound.put("protocol", "socks")
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(server)))
            }

            Protocol.HTTP -> {
                val server = JSONObject().put("address", profile.address).put("port", profile.port)
                if (profile.username.isNotBlank()) {
                    server.put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("user", profile.username)
                                .put("pass", profile.password),
                        ),
                    )
                }
                outbound.put("protocol", "http")
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(server)))
            }

            Protocol.CUSTOM -> outbound.put("protocol", "freedom")
        }

        outbound.put("mux", JSONObject().put("enabled", false))
        return outbound
    }

    private fun normalizeNetwork(network: String): String = when (network.lowercase()) {
        "h2", "http" -> "h2"
        "ws", "websocket" -> "ws"
        "grpc", "gun" -> "grpc"
        "quic" -> "quic"
        "xhttp", "splithttp" -> "xhttp"
        "kcp", "mkcp" -> "kcp"
        else -> "tcp"
    }

    /** برای کانفیگ‌های JSON دستی: (آدرس، پورت) اولین سرور، برای نمایش در لیست */
    fun describeCustom(json: String): Pair<String, Int> {
        return try {
            val root = JSONObject(json)
            val outbounds = root.optJSONArray("outbounds") ?: return "" to 0
            for (i in 0 until outbounds.length()) {
                val settings = outbounds.optJSONObject(i)?.optJSONObject("settings") ?: continue
                settings.optJSONArray("vnext")?.optJSONObject(0)?.let { node ->
                    return node.optString("address") to node.optInt("port")
                }
                settings.optJSONArray("servers")?.optJSONObject(0)?.let { node ->
                    return node.optString("address") to node.optInt("port")
                }
            }
            "" to 0
        } catch (t: Throwable) {
            "" to 0
        }
    }
}
