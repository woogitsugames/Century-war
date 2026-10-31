package com.centurywar.engine

import java.net.URI

/** Zewnętrzne odnośniki są otwierane tylko dla adresów HTTPS. */
object SecureLinks {
    fun isHttps(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.userInfo == null
    }.getOrDefault(false)
}
