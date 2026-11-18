package com.yehor.finora.domain.tag

import java.util.Locale

enum class TagNameError {
    EMPTY,
    TOO_LONG,
}

object TagNameRules {
    const val MAX_LENGTH = 30
    private val whitespaceRegex = Regex("""\s+""")

    fun normalize(raw: String): String =
        raw.trim().replace(whitespaceRegex, " ")

    fun key(name: String): String =
        normalize(name).lowercase(Locale.ROOT)

    fun validate(raw: String): TagNameError? {
        val normalized = normalize(raw)
        return when {
            normalized.isEmpty() -> TagNameError.EMPTY
            normalized.codePointCount(0, normalized.length) > MAX_LENGTH -> TagNameError.TOO_LONG
            else -> null
        }
    }
}
