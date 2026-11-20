
package com.webtextscraper.service;

import com.webtextscraper.model.ScrapeResult;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class WebScrapeService {

    private static final int MAX_TEXT_LENGTH = 100000;
    private static final int TIMEOUT_MS = 3000;
    private static final int MAX_BODY_BYTES = 1_500_000;

    private static final Set<String> BLOCK_TAGS = Set.of(
            "h1", "h2", "h3", "h4", "h5", "h6",
            "p", "li", "blockquote", "figcaption",
            "pre", "dt", "dd"
    );

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .dns(new SafeDns())
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

    public ScrapeResult scrape(String url) throws IOException {

        String cleanUrl = validateUrl(url);

        Request request = new Request.Builder()
                .url(cleanUrl)
                .header("User-Agent",
                        "Mozilla/5.0 (compatible; WebTextScraper/1.0)")
                .header("Accept", "text/html")
                .get()
                .build();

        Document document;

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException(
                        "Website returned HTTP " + response.code()
                );
            }

            ResponseBody body = response.body();

            if (body == null) {
                throw new IOException("Website returned no content.");
            }

            String contentType = response.header("Content-Type", "");

            if (!contentType.isBlank()
                    && !contentType.toLowerCase(Locale.ROOT)
                    .contains("text/html")
                    && !contentType.toLowerCase(Locale.ROOT)
                    .contains("application/xhtml+xml")) {
                throw new IOException("Website did not return HTML.");
            }

            if (body.contentLength() > MAX_BODY_BYTES) {
                throw new IOException("Website response is too large.");
            }

            byte[] htmlBytes;

            try (InputStream input = body.byteStream()) {
                htmlBytes = input.readNBytes(MAX_BODY_BYTES + 1);
            }

            if (htmlBytes.length > MAX_BODY_BYTES) {
                throw new IOException("Website response is too large.");
            }

            document = Jsoup.parse(
                    new java.io.ByteArrayInputStream(htmlBytes),
                    null,
                    cleanUrl
            );
        }

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

        return new ScrapeResult(cleanUrl, title, extractedText);
    }

    private String validateUrl(String url) {

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Please enter a website URL."
            );
        }

        if (url.length() > 2048) {
            throw new IllegalArgumentException("URL is too long.");
        }

        try {
            URI uri = URI.create(url.trim());

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http")
                    && !scheme.equalsIgnoreCase("https"))
                    || host == null
                    || host.isBlank()
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "Please enter a valid HTTP or HTTPS URL."
                );
            }

            if (uri.getPort() != -1
                    && uri.getPort() != 80
                    && uri.getPort() != 443) {
                throw new IllegalArgumentException(
                        "Only standard HTTP and HTTPS ports are allowed."
                );
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);

            if (normalizedHost.equals("localhost")
                    || normalizedHost.endsWith(".localhost")
                    || normalizedHost.endsWith(".local")
                    || normalizedHost.endsWith(".internal")
                    || normalizedHost.endsWith(".test")
                    || !normalizedHost.contains(".")) {
                throw new IllegalArgumentException(
                        "Local or internal website URLs are not allowed."
                );
            }

            return uri.toString();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid website URL.", e
            );
        }
    }

    private static class SafeDns implements Dns {

        @Override
        public List<InetAddress> lookup(String hostname)
                throws java.net.UnknownHostException {

            List<InetAddress> addresses =
                    Arrays.asList(InetAddress.getAllByName(hostname));

            if (addresses.isEmpty()) {
                throw new java.net.UnknownHostException(
                        "No IP addresses found."
                );
            }

            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new java.net.UnknownHostException(
                            "Private or restricted network address blocked."
                    );
                }
            }

            return addresses;
        }

        private static boolean isPublicAddress(InetAddress address) {

            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }

            byte[] bytes = address.getAddress();

            if (address instanceof Inet4Address) {

                int a = bytes[0] & 255;
                int b = bytes[1] & 255;
                int c = bytes[2] & 255;

                if (a == 0 || a == 10 || a == 127
                        || a >= 224
                        || (a == 100 && b >= 64 && b <= 127)
                        || (a == 169 && b == 254)
                        || (a == 172 && b >= 16 && b <= 31)
                        || (a == 192 && b == 168)
                        || (a == 192 && b == 0 && c == 0)
                        || (a == 192 && b == 0 && c == 2)
                        || (a == 192 && b == 88 && c == 99)
                        || (a == 198 && (b == 18 || b == 19))
                        || (a == 198 && b == 51 && c == 100)
                        || (a == 203 && b == 0 && c == 113)) {
                    return false;
                }
            }

            if (address instanceof Inet6Address) {

                int first = bytes[0] & 255;

                if ((first & 0xFE) == 0xFC
                        || (first & 0xFF) == 0xFF) {
                    return false;
                }

                // Reject IPv4-mapped and other IPv4-embedded
                // transition address formats conservatively.
                boolean prefixZero = true;

                for (int i = 0; i < 8; i++) {
                    if (bytes[i] != 0) {
                        prefixZero = false;
                        break;
                    }
                }

                if (prefixZero) {
                    return false;
                }

                // Reject IPv6 documentation addresses.
                if (first == 0x20
                        && (bytes[1] & 255) == 0x01
                        && (bytes[2] & 255) == 0x0D
                        && (bytes[3] & 255) == 0xB8) {
                    return false;
                }
            }

            return true;
        }
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
}
