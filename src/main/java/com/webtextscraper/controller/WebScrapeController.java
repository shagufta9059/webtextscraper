
package com.webtextscraper.controller;

import com.webtextscraper.model.ScrapeRequest;
import com.webtextscraper.model.ScrapeResult;
import com.webtextscraper.service.WebScrapeService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class WebScrapeController {

    private final WebScrapeService webScrapeService;

    public WebScrapeController(WebScrapeService webScrapeService) {
        this.webScrapeService = webScrapeService;
    }

    @PostMapping("/scrape")
    public ResponseEntity<?> scrapeWebsite(
            @RequestBody ScrapeRequest request) {

        try {
            if (request == null ||
                    request.getUrl() == null ||
                    request.getUrl().isBlank()) {

                return ResponseEntity.badRequest().body(
                        Map.of("error", "Please enter a website URL.")
                );
            }

            ScrapeResult result =
                    webScrapeService.scrape(request.getUrl());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );

        } catch (Exception e) {

            return ResponseEntity.status(
                    HttpStatus.BAD_GATEWAY
            ).body(
                    Map.of("error",
                            "Unable to extract text from this website.")
            );
        }
    }
}
