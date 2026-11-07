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
                match.groupValues[3] +
                """<script src="https://xilon-preview.invalid/GLTFExporter.js"></script>"""
        }
        result = moduleUrl.replace(result) { match ->
            match.groupValues[1] + "three" + match.groupValues[3]
        }
        if (!bareImport.containsMatchIn(result)) return result
        val prefix = """<script src="https://xilon-preview.invalid/three.min.js"></script>
<script src="https://xilon-preview.invalid/GLTFExporter.js"></script>
<script type="importmap">{"imports":{"three":"https://xilon-preview.invalid/three-module-adapter.js"}}</script>"""
        val head = Regex("""(?i)<head\b[^>]*>""")
        val match = head.find(result)
        return if (match != null) result.replaceRange(match.range, match.value + prefix)
            else prefix + result
    }
}
