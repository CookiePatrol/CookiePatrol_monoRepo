package com.cookiepatrol.warn

internal data class WarningMessage(val sender: String?, val preview: String, val timeMillis: Long) {
    companion object {
        fun from(sender: String?, text: String, timeMillis: Long) = WarningMessage(
            sender?.let { excerpt(it, 48) }?.takeIf { it.isNotEmpty() },
            excerpt(text, 160),
            timeMillis,
        )

        private fun excerpt(raw: String, limit: Int): String {
            val clean = buildString {
                raw.forEach { char ->
                    when {
                        char.isWhitespace() -> if (isNotEmpty() && last() != ' ') append(' ')
                        !char.isISOControl() && char.category != CharCategory.FORMAT -> append(char)
                    }
                }
            }.trimEnd()
            val codePoints = clean.codePointCount(0, clean.length)
            return if (codePoints <= limit) clean else clean.substring(0, clean.offsetByCodePoints(0, limit)) + "…"
        }
    }
}
