package com.xilonalpha.xilonai

/** Extracts a complete HTML document from model output, including fenced code. */
object XilonHtmlExport {
    fun extract(answer: String): String? {
        val start = answer.indexOf("<!DOCTYPE html", ignoreCase = true)
            .takeIf { it >= 0 } ?: answer.indexOf("<html", ignoreCase = true)
        if (start < 0) return null
        val close = answer.indexOf("</html>", start, ignoreCase = true)
        if (close < 0) return null
        return answer.substring(start, close + "</html>".length)
    }
}
