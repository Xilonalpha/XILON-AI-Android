package com.xilonalpha.xilonai

/**
 * Rewrites only recognized classic Three.js script references to the bundled
 * local copy. ES-module imports require a separate module-compatible pipeline.
 */
object XilonThreeOffline {
    private val classicScript = Regex(
        """(?i)(<script\b[^>]*\bsrc\s*=\s*["'])(https?://[^"']*/(?:three(?:\.min)?\.js|build/three(?:\.min)?\.js)(?:\?[^"']*)?)(["'][^>]*>\s*</script>)"""
    )
    private val moduleUrl = Regex(
        """(?i)(\bfrom\s*["'])(https?://[^"']*/three\.module(?:\.min)?\.js(?:\?[^"']*)?)(["'])"""
    )
    private val bareImport = Regex("""\bfrom\s*["']three["']""")

    fun prepare(html: String): String {
        var result = classicScript.replace(html) { match ->
            match.groupValues[1] + "https://xilon-preview.invalid/three.min.js" +
                match.groupValues[3]
        }
        result = moduleUrl.replace(result) { match ->
            match.groupValues[1] + "three" + match.groupValues[3]
        }
        val usesModule = bareImport.containsMatchIn(result)
        val usesClassic = result.contains("https://xilon-preview.invalid/three.min.js")
        if (!usesModule && !usesClassic) return result

        // Insert once, before user scripts. Import maps must precede module execution.
        val loader = buildString {
            if (usesModule && !usesClassic) {
                append("""<script src="https://xilon-preview.invalid/three.min.js"></script>""")
            }
            append("""<script src="https://xilon-preview.invalid/GLTFExporter.js"></script>""")
            if (usesModule) {
                append("""<script type="importmap">{"imports":{"three":"https://xilon-preview.invalid/three-module-adapter.js"}}</script>""")
            }
        }
        if (usesClassic && !usesModule) {
            val script = Regex("""(?i)<script\\b[^>]*\\bsrc\\s*=\\s*["']https://xilon-preview\\.invalid/three\\.min\\.js["'][^>]*>\\s*</script>""")
            val found = script.find(result)
            if (found != null) return result.replaceRange(found.range, found.value + loader)
        }
        val head = Regex("""(?i)<head\\b[^>]*>""")
        val found = head.find(result)
        return if (found != null) result.replaceRange(found.range, found.value + loader)
            else loader + result
    }
}
