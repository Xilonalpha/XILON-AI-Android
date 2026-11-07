package com.xilonalpha.xilonai

/**
 * Rewrites only recognized classic Three.js script references to the bundled
 * local copy. ES-module imports require a separate module-compatible pipeline.
 */
object XilonThreeOffline {
    private val classicScript = Regex(
        """(?i)(<script\b[^>]*\bsrc\s*=\s*["'])(https?://[^"']*/(?:three(?:\.min)?\.js|build/three(?:\.min)?\.js)(?:\?[^"']*)?)(["'][^>]*>\s*</script>)"""
    )
    fun prepare(html: String): String =
        classicScript.replace(html) { match ->
            match.groupValues[1] + "https://xilon-preview.invalid/three.min.js" +
                match.groupValues[3]
        }
}
