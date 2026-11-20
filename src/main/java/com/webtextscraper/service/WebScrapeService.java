
package com.webtextscraper.service;

import com.webtextscraper.model.ScrapeResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WebScrapeService {

    private static final int MAX_TEXT_LENGTH = 100000;
    private static final int TIMEOUT_MS =3000;

    private static final Set<String> BLOCK_TAGS = Set.of(
            "h1", "h2", "h3", "h4", "h5", "h6",
            "p", "li", "blockquote", "figcaption",
            "pre", "dt", "dd"
    );

    public ScrapeResult scrape(String url) throws IOException {

        validateUrl(url);

        Document document = Jsoup.connect(url.trim())
                .userAgent("Mozilla/5.0 (compatible; WebTextScraper/1.0)")
                .timeout(TIMEOUT_MS)
                .maxBodySize(1_500_000)
                .followRedirects(false)
                .get();

        String title = document.title();

        document.select(
                "script, style, noscript, iframe, svg, canvas, " +
                        "nav, header, footer, form, button, input, " +
                        "select, textarea, aside, " +
                        "[role=navigation], [role=banner], " +
                        "[role=contentinfo], [role=dialog], " +
                        "[aria-hidden=true], " +
                        ".advertisement, .advert, .ad-container, " +
                        ".ads, .popup, .modal, .cookie-banner, " +
                        ".newsletter-signup, .social-share"
        ).remove();

        Element main = findMainContent(document);

        if (main == null) {
            main = document.body();
        }

        String headline = "";
        Element h1 = main.selectFirst("h1");

        if (h1 != null) {
            headline = cleanText(h1.text());
        }

        List<String> lines = new ArrayList<>();

        if (!headline.isBlank()) {
            lines.add(headline);
        }

        for (Element element : main.select(
                "h1, h2, h3, h4, h5, h6, " +
                        "p, li, blockquote, figcaption, pre, dt, dd"
        )) {

            if (hasSelectedAncestor(element, main)) {
                continue;
            }

            String text = cleanText(element.text());

            if (text.isBlank()
                    || text.equals(headline)
                    || isUnwantedText(text)) {
                continue;
            }

            if (lines.isEmpty()
                    || !lines.get(lines.size() - 1).equals(text)) {
                lines.add(text);
            }
        }

        String extractedText = String.join("\n\n", lines);

        if (extractedText.isBlank()) {
            extractedText = cleanText(main.text());
        }

        if (extractedText.length() > MAX_TEXT_LENGTH) {
            extractedText = extractedText.substring(0, MAX_TEXT_LENGTH);
        }

        return new ScrapeResult(
                url.trim(),
                title,
                extractedText
        );
    }

    private Element findMainContent(Document document) {

        String[] selectors = {
                "main",
                "[role=main]",
                "article",
                ".article-content",
                ".article-body",
                ".post-content",
                ".entry-content",
                "#main-content",
                "#content"
        };

        for (String selector : selectors) {
            Elements matches = document.select(selector);

            for (Element element : matches) {
                if (element.text().length() > 200) {
                    return element;
                }
            }
        }

        return document.body();
    }

    private boolean hasSelectedAncestor(
            Element element, Element root) {

        Element parent = element.parent();

        while (parent != null && parent != root) {
            if (BLOCK_TAGS.contains(parent.normalName())) {
                return true;
            }
            parent = parent.parent();
        }

        return false;
    }

    private String cleanText(String text) {
        return text
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\r\\n]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private boolean isUnwantedText(String text) {

        String lower = text.toLowerCase(Locale.ROOT);

        return lower.equals("advertisement")
                || lower.equals("advert")
                || lower.equals("skip to content")
                || lower.equals("sign in")
                || lower.equals("log in")
                || lower.equals("subscribe now")
                || lower.equals("accept cookies")
                || lower.equals("privacy policy")
                || lower.equals("terms of service");
    }

    private void validateUrl(String url) {

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter a website URL."
            );
        }

        try {
            URI uri = URI.create(url.trim());

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null ||
                    (!scheme.equalsIgnoreCase("http")
                            && !scheme.equalsIgnoreCase("https"))
                    || host == null) {

                throw new IllegalArgumentException(
                        "Please enter a valid HTTP or HTTPS URL."
                );
            }

            String normalizedHost =
                    host.toLowerCase(Locale.ROOT);

            if (normalizedHost.equals("localhost")
                    || normalizedHost.endsWith(".localhost")
                    || normalizedHost.endsWith(".local")) {

                throw new IllegalArgumentException(
                        "Local website URLs are not allowed."
                );
            }

            for (InetAddress address :
                    InetAddress.getAllByName(host)) {

                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {

                    throw new IllegalArgumentException(
                            "Private or local network URLs are not allowed."
                    );
                }
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid website URL.", e
            );
        }
    }
}
