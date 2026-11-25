# WebTextScraper - Full-Stack Web Content Extraction Service

**Live Website link:** https://webtextscraper-frontend.onrender.com/  
**GitHub (Backend / Frontend):** [https://webtextscraper-frontend.onrender.com/](https://github.com/shagufta9059/webtextscraper)

## Overview
WebTextScraper is a full-stack web application built with **React** and **Spring Boot** that extracts, cleans, and structures readable article text from public URLs in real time.

## Key Features
- **Automated HTML Parsing:** Extracts core headings and paragraphs while stripping navigation bars, ads, scripts, and boilerplate HTML using JSoup.
- **RESTful API Architecture:** Clean separation between React client and Spring Boot backend with CORS support and structured JSON error responses.
- **User-Friendly Interface:** Responsive React + Vite UI with instant URL validation, loading states, and one-click clipboard copy.

## Tech Stack
- **Frontend:** React, Vite, JavaScript (ES6+), CSS
- **Backend:** Java, Spring Boot, Maven, REST API
- **HTML Parsing:** JSoup
- **Deployment:** Render

## Engineering Challenges & Solutions
1. **Noisy HTML Extraction:** Refined JSoup CSS selectors and DOM filtering to isolate meaningful body content while removing scripts, styles, and hidden tags.
2. **Unresponsive External Sites:** Configured explicit connection and read timeouts in JSoup alongside global `@ControllerAdvice` exception handling to return clean HTTP error messages instead of hanging requests.
3. **Frontend-Backend Integration:** Resolved cross-origin (CORS) and payload formatting issues between the Vite/React client and Spring Boot REST endpoints.
