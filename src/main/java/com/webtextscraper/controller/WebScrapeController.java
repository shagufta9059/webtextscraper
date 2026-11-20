
package com.webtextscraper.controller;

import com.webtextscraper.model.ScrapeRequest;
import com.webtextscraper.model.ScrapeResult;
import com.webtextscraper.service.WebScrapeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://webtextscraper-frontend.onrender.com"
})
public class WebScrapeController {

    private final WebScrapeService webScrapeService;

    // Limit simultaneous scraping requests
    private final Semaphore requestLimiter = new Semaphore(3);

    public WebScrapeController(WebScrapeService webScrapeService) {
        this.webScrapeService = webScrapeService;
    }

    @PostMapping("/scrape")
    public ResponseEntity<?> scrapeWebsite(@RequestBody ScrapeRequest request) {

        if (request == null || request.getUrl() == null ||
                request.getUrl().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Please enter a website URL.")
            );
        }

        boolean acquired = false;

        try {
            acquired = requestLimiter.tryAcquire(
                    100, TimeUnit.MILLISECONDS
            );

            if (!acquired) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error",
                                "Server is busy. Please try again shortly."));
            }

            ScrapeResult result = webScrapeService.scrape(request.getUrl());
            return ResponseEntity.ok(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Request interrupted."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error",
                            "Unable to extract text from this website."));

        } finally {
            if (acquired) {
                requestLimiter.release();
            }
        }
    }
}
