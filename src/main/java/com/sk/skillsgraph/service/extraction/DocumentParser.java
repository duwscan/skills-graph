package com.sk.skillsgraph.service.extraction;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class DocumentParser {

    public String parse(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (looksLikeHtml(normalized)) {
            normalized = normalized
                    .replaceAll("(?i)<h[1-6][^>]*>", "\n\n")
                    .replaceAll("(?i)</h[1-6]>", "\n\n")
                    .replaceAll("(?i)<p[^>]*>", "\n\n")
                    .replaceAll("(?i)</p>", "\n\n")
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)<li[^>]*>", "\n- ")
                    .replaceAll("(?i)</li>", "")
                    .replaceAll("(?i)<ul[^>]*>", "\n")
                    .replaceAll("(?i)</ul>", "\n")
                    .replaceAll("(?i)<ol[^>]*>", "\n")
                    .replaceAll("(?i)</ol>", "\n")
                    .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                    .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                    .replaceAll("(?s)<[^>]+>", " ");
            normalized = HtmlUtils.htmlUnescape(normalized);
        }

        normalized = normalized
                .replace('\t', ' ')
                .replaceAll("[\\u00A0\\s&&[^\\n]]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return normalized;
    }

    private boolean looksLikeHtml(String text) {
        return text.contains("<") && text.contains(">");
    }
}
