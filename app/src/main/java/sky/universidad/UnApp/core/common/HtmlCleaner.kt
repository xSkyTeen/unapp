package sky.universidad.UnApp.core.common

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object HtmlCleaner {
    fun toPlainText(rawHtml: String?): String {
        if (rawHtml.isNullOrBlank()) return ""
        val doc: Document = Jsoup.parse(rawHtml)
        doc.select("br").append("\\n")
        doc.select("p, li, div, h1, h2, h3, h4").prepend("\\n")
        val text = doc.text().replace("\\n", "\n")
        return text
            .lines()
            .map { it.trim() }
            .filterIndexed { index, line -> line.isNotEmpty() || index > 0 }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
