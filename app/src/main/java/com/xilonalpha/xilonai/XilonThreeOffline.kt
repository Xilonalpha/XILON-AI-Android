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

    private val diagnostics = """
<script>
(function(){
  function report(message) {
    if (document.getElementById('xilon-preview-error')) return;
    var panel = document.createElement('div');
    panel.id = 'xilon-preview-error';
    panel.style.cssText = 'position:fixed;left:8px;right:8px;bottom:8px;z-index:2147483647;background:#451c29;color:#fff;padding:12px;border-radius:8px;font:13px sans-serif;white-space:pre-wrap;max-height:45%;overflow:auto';
    panel.textContent = 'Eroare previzualizare 3D: ' + String(message).slice(0,500);
    (document.body || document.documentElement).appendChild(panel);
  }
  window.addEventListener('error', function(e){ report(e.message || 'JavaScript / resursă indisponibilă'); });
  window.addEventListener('unhandledrejection', function(e){ report(e.reason && e.reason.message || e.reason || 'Modul JavaScript indisponibil'); });
  document.addEventListener('webglcontextcreationerror', function(e){ report(e.statusMessage || 'WebGL indisponibil pe dispozitiv'); }, true);
})();
</script>
""".trimIndent()

    fun prepare(html: String): String {
        var result = classicScript.replace(html) { match ->
            match.groupValues[1] + "https://xilon-preview.invalid/three.min.js" +
                match.groupValues[3]
        }
        result = moduleUrl.replace(result) { match ->
            match.groupValues[1] + "three" + match.groupValues[3]
        }
        // Bundled generated scenes use a relative classic script URL.
        // Rewrite it to the same trusted virtual origin handled by WebViewClient.
        result = result.replace(
            Regex("""(?i)(<script\b[^>]*\bsrc\s*=\s*["'])three(?:\.min)?\.js(["'][^>]*>\s*</script>)"""),
            "$1https://xilon-preview.invalid/three.min.js$2"
        )
        val usesModule = bareImport.containsMatchIn(result)
        val usesClassic = result.contains("https://xilon-preview.invalid/three.min.js")
        if (!usesModule && !usesClassic) return injectDiagnostics(result)

        // Insert once, before user scripts. Import maps must precede module execution.
        val loader = buildString {
            if (usesModule) {
                append("""<script src="https://xilon-preview.invalid/three.min.js"></script>""")
            }
            append("""<script src="https://xilon-preview.invalid/GLTFExporter.js"></script>""")
            if (usesModule) {
                append("""<script type="importmap">{"imports":{"three":"https://xilon-preview.invalid/three-module-adapter.js"}}</script>""")
            }
        }
        if (usesClassic && !usesModule) {
            val script = Regex("""(?i)<script\b[^>]*\bsrc\s*=\s*["']https://xilon-preview\.invalid/three\.min\.js["'][^>]*>\s*</script>""")
            val found = script.find(result)
            if (found != null) return injectDiagnostics(result.replaceRange(found.range, found.value + loader))
        }
        val head = Regex("""(?i)<head\b[^>]*>""")
        val found = head.find(result)
        return injectDiagnostics(if (found != null) result.replaceRange(found.range, found.value + loader)
            else loader + result)
    }
    private fun injectDiagnostics(html: String): String {
        val head = Regex("""(?i)<head\b[^>]*>""").find(html)
        return if (head != null) html.replaceRange(head.range, head.value + diagnostics)
            else diagnostics + html
    }

}
