package com.webtextscraper.service;

import com.webtextscraper.model.ScrapeResult;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class WebScrapeService {

    private static final int MAX_TEXT_LENGTH = 100000;
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_BODY_BYTES = 1_500_000;

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
        Document document = fetchDocument(cleanUrl);
        String title = document.title();

        document.select("script, style, noscript, iframe, svg").remove();

        document.select("p, h1, h2, h3, li").append(" PARAGRAPH_BREAK ");

        String extractedText = document.body().text()
                .replace("PARAGRAPH_BREAK", "\n\n").trim();

        if (extractedText.length() > MAX_TEXT_LENGTH) {
            extractedText = extractedText.substring(0, MAX_TEXT_LENGTH);
        }

        return new ScrapeResult(cleanUrl, title, extractedText);
    }
    private Document fetchDocument(String url) throws IOException {

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent",
                        "Mozilla/5.0 (compatible; WebTextScraper/1.0)")
                .header("Accept", "text/html")
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build();

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

            String contentType = response.header("Content-Type", "")
                    .toLowerCase(Locale.ROOT);

            if (!contentType.isBlank()
                    && !contentType.contains("text/html")
                    && !contentType.contains("application/xhtml+xml")) {

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

            return Jsoup.parse(
                    new java.io.ByteArrayInputStream(htmlBytes),
                    null,
                    url
            );
        }
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
    }

    private static class SafeDns implements Dns {

        @Override
        public List<InetAddress> lookup(String hostname)
                throws UnknownHostException {

            List<InetAddress> addresses =
                    Arrays.asList(InetAddress.getAllByName(hostname));

            if (addresses.isEmpty()) {
                throw new UnknownHostException("No IP addresses found.");
            }

            for (InetAddress address : addresses) {

                if (!isPublicAddress(address)) {
                    throw new UnknownHostException(
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
                        || first == 0xFF) {

                    return false;
                }

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
}