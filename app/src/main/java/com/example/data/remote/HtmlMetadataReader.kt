package com.example.data.remote

/**
 * The parts of an HTML page that describe it: `<meta>` and `<link>` tags, the `<title>`,
 * JSON-LD blocks and microdata (`itemprop`) values. Attribute values are entity-decoded.
 */
internal data class HtmlMetadata(
    val title: String?,
    val metaTags: List<Map<String, String>>,
    val linkTags: List<Map<String, String>>,
    val jsonLdBlocks: List<String>,
    val itemProps: Map<String, List<String>>
) {
    /** Content of the first meta tag whose property, name or itemprop equals [key] (case-insensitive). */
    fun meta(key: String): String? = metaTags.firstNotNullOfOrNull { tag ->
        val matches = tag["property"].equals(key, ignoreCase = true) ||
            tag["name"].equals(key, ignoreCase = true) ||
            tag["itemprop"].equals(key, ignoreCase = true)
        tag["content"]?.trim()?.takeIf { matches && it.isNotEmpty() }
    }

    fun linkHref(rel: String): String? = linkTags.firstNotNullOfOrNull { tag ->
        val rels = tag["rel"]?.lowercase()?.split(' ').orEmpty()
        tag["href"]?.trim()?.takeIf { rel in rels && it.isNotEmpty() }
    }

    fun itemProp(name: String): List<String> = itemProps[name.lowercase()].orEmpty()
}

/**
 * A small, forgiving tag scanner. It is not a full HTML parser: it only collects page metadata,
 * skipping comments and the contents of scripts and styles so their text is never mistaken for tags.
 */
internal object HtmlMetadataReader {
    private val rawTextTags = setOf("script", "style", "title", "textarea")

    fun read(html: String): HtmlMetadata {
        var title: String? = null
        val metas = mutableListOf<Map<String, String>>()
        val links = mutableListOf<Map<String, String>>()
        val jsonLd = mutableListOf<String>()
        val itemProps = linkedMapOf<String, MutableList<String>>()

        val n = html.length
        var i = 0
        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0 || lt + 1 >= n) break
            val next = html[lt + 1]
            when {
                html.startsWith("<!--", lt) -> {
                    val end = html.indexOf("-->", lt + 4)
                    i = if (end < 0) n else end + 3
                    continue
                }
                next == '/' || next == '!' || next == '?' -> {
                    val end = html.indexOf('>', lt + 1)
                    i = if (end < 0) n else end + 1
                    continue
                }
                !next.isLetter() -> {
                    i = lt + 1
                    continue
                }
            }

            var k = lt + 1
            while (k < n && (html[k].isLetterOrDigit() || html[k] == '-' || html[k] == ':')) k++
            val tagName = html.substring(lt + 1, k).lowercase()

            val attrs = linkedMapOf<String, String>()
            while (k < n) {
                val c = html[k]
                if (c == '>') {
                    k++
                    break
                }
                if (c.isWhitespace() || c == '/') {
                    k++
                    continue
                }
                val nameStart = k
                while (k < n && !html[k].isWhitespace() && html[k] != '=' && html[k] != '>' && html[k] != '/') k++
                val attrName = html.substring(nameStart, k).lowercase()
                while (k < n && html[k].isWhitespace()) k++
                var value = ""
                if (k < n && html[k] == '=') {
                    k++
                    while (k < n && html[k].isWhitespace()) k++
                    if (k < n && (html[k] == '"' || html[k] == '\'')) {
                        val close = html.indexOf(html[k], k + 1)
                        val end = if (close < 0) n else close
                        value = html.substring(k + 1, end)
                        k = if (close < 0) n else close + 1
                    } else {
                        val valueStart = k
                        while (k < n && !html[k].isWhitespace() && html[k] != '>') k++
                        value = html.substring(valueStart, k)
                    }
                }
                if (attrName.isNotEmpty() && attrName !in attrs) attrs[attrName] = HtmlText.decodeEntities(value)
            }
            val contentStart = k

            when (tagName) {
                "meta" -> metas += attrs
                "link" -> links += attrs
            }

            attrs["itemprop"]?.let { props ->
                val value = attrs["content"] ?: attrs["src"] ?: attrs["href"]
                    ?: if (tagName !in rawTextTags) html.substring(contentStart, html.indexOf('<', contentStart).let { if (it < 0) n else it }) else null
                val cleaned = value?.let { HtmlText.collapseWhitespace(HtmlText.decodeEntities(it)) }
                if (!cleaned.isNullOrEmpty()) {
                    props.lowercase().split(' ').filter { it.isNotEmpty() }.forEach { prop ->
                        itemProps.getOrPut(prop) { mutableListOf() } += cleaned
                    }
                }
            }

            if (tagName in rawTextTags) {
                val close = html.indexOf("</$tagName", contentStart, ignoreCase = true)
                val content = html.substring(contentStart, if (close < 0) n else close)
                if (tagName == "title" && title == null) title = content
                if (tagName == "script" && attrs["type"]?.trim()?.lowercase()?.startsWith("application/ld+json") == true) {
                    jsonLd += content
                }
                i = if (close < 0) n else html.indexOf('>', close).let { if (it < 0) n else it + 1 }
            } else {
                i = contentStart
            }
        }

        return HtmlMetadata(title, metas, links, jsonLd, itemProps)
    }
}

internal object HtmlText {
    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“",
        "rdquo" to "”", "bdquo" to "„", "hellip" to "…", "trade" to "™", "reg" to "®", "copy" to "©",
        "times" to "×", "divide" to "÷", "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
        "deg" to "°", "middot" to "·", "bull" to "•", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "laquo" to "«", "raquo" to "»", "plusmn" to "±", "micro" to "µ", "sect" to "§", "shy" to "",
        "zwj" to "", "zwnj" to "", "eacute" to "é", "egrave" to "è", "aacute" to "á", "agrave" to "à",
        "ouml" to "ö", "uuml" to "ü", "auml" to "ä", "ccedil" to "ç", "ntilde" to "ñ", "szlig" to "ß"
    )
    private val whitespace = Regex("\\s+")
    private val tags = Regex("<[^>]*>")

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '&') {
                val semicolon = text.indexOf(';', i + 1)
                if (semicolon in (i + 2)..(i + 12)) {
                    val entity = text.substring(i + 1, semicolon)
                    val decoded = if (entity.startsWith("#")) decodeNumeric(entity) else namedEntities[entity]
                    if (decoded != null) {
                        out.append(decoded)
                        i = semicolon + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    fun collapseWhitespace(text: String): String = text.replace(whitespace, " ").trim()

    /**
     * Plain text from a value that may contain markup; null when nothing is left.
     * Pass [decode] = false for attribute values, which are already entity-decoded.
     */
    fun plain(text: String?, decode: Boolean = true): String? = text
        ?.replace(tags, " ")
        ?.let { if (decode) decodeEntities(it) else it }
        ?.let(::collapseWhitespace)
        ?.takeIf { it.isNotEmpty() }

    private fun decodeNumeric(entity: String): String? {
        val code = if (entity.length > 2 && (entity[1] == 'x' || entity[1] == 'X')) {
            entity.substring(2).toIntOrNull(16)
        } else {
            entity.substring(1).toIntOrNull()
        }
        return code?.takeIf { it in 1..0x10FFFF && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it)) }
    }
}
