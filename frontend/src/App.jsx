
import { useState, useRef, useEffect } from "react";
import "./App.css";

const REQUEST_TIMEOUT = 60000;
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

function App() {
  const [url, setUrl] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  const inputRef = useRef(null);
  const requestRef = useRef(null);
  const copyTimerRef = useRef(null);

  useEffect(() => {
    return () => {
      requestRef.current?.abort();
      clearTimeout(copyTimerRef.current);
    };
  }, []);

  async function extractText(event) {
    event.preventDefault();

    if (loading) return;

    const websiteUrl = url.trim();

    if (!websiteUrl) {
      setError("Please enter a website URL.");
      return;
    }

    const controller = new AbortController();
    requestRef.current = controller;

    setLoading(true);
    setError("");
    setResult(null);
    setCopied(false);

    const timeoutId = setTimeout(() => {
      controller.abort(
        new DOMException(
          "Website extraction timed out.",
          "TimeoutError"
        )
      );
    }, REQUEST_TIMEOUT);

    try {
      const response = await fetch(`${API_BASE_URL}/api/scrape`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ url: websiteUrl }),
        signal: controller.signal,
      });

      if (!response.ok) {
        let message = "Unable to extract text from this website.";

        try {
          const errorData = await response.json();
          message = errorData.error || message;
        } catch {
          // Use the default error message.
        }

        throw new Error(message);
      }

      const data = await response.json();

      if (!data.text || !data.text.trim()) {
        throw new Error(
          "No readable text was found on this webpage."
        );
      }

      setResult(data);
    } catch (err) {
      if (
        controller.signal.aborted ||
        err.name === "TimeoutError" ||
        err.name === "AbortError"
      ) {
        setError(
          "This request took too long. Please try again."
        );
      } else {
        setError(err.message || "Something went wrong.");
      }
    } finally {
      clearTimeout(timeoutId);
      requestRef.current = null;
      setLoading(false);
    }
  }

  async function copyText() {
    if (!result?.text) return;

    try {
      await navigator.clipboard.writeText(result.text);
      setCopied(true);

      clearTimeout(copyTimerRef.current);

      copyTimerRef.current = setTimeout(() => {
        setCopied(false);
      }, 2000);
    } catch {
      setError("Could not copy text to clipboard.");
    }
  }

  function focusInput() {
    inputRef.current?.focus();
    inputRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "center",
    });
  }

  return (
    <div className="site">
      <header className="navbar">
        <div className="nav-container">
          <a className="brand" href="#home">
            <span className="brand-mark">W</span>
            <span>WebTextScraper</span>
          </a>

          <nav className="nav-links">
            <a href="#features">Features</a>
            <a href="#how-it-works">How it Works</a>
            <a href="#tech-stack">Tech Stack</a>
          </nav>

          <button
            className="get-started"
            type="button"
            onClick={focusInput}
          >
            Get Started
          </button>
        </div>
      </header>

      <main id="home">
        <div className="hero">
          <div className="hero-icon">
            <svg
              width="46"
              height="46"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <rect x="5" y="2" width="14" height="20" rx="2" />
              <path d="M9 8h6M9 12h6M9 16h4" />
            </svg>
          </div>

          <h1>
            Extract web text with{" "}
            <span className="gradient-text">ease.</span>
          </h1>

          <p className="hero-description">
            Turn any public webpage into clean, readable text —
            fast, simple, and distraction-free.
          </p>

          <form
            className="search-form"
            onSubmit={extractText}
            id="extract"
          >
            <div className="url-field">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
                <path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
              </svg>

              <input
                ref={inputRef}
                type="url"
                placeholder="Enter website URL (e.g. https://example.com)"
                value={url}
                onChange={(event) => setUrl(event.target.value)}
                required
                aria-label="Website URL"
              />
            </div>

            <button
              className="extract-btn"
              type="submit"
              disabled={loading}
            >
              {loading ? "Extracting..." : "Extract Text"}
              {!loading && <span aria-hidden="true">→</span>}
            </button>
          </form>

          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}
        </div>

        <section className="result-card" aria-live="polite">
          <div className="result-topbar">
            <div className="result-heading">
              <span className="result-icon">▤</span>
              <h2>Extracted Text</h2>
            </div>

            <button
              className="copy-btn"
              type="button"
              onClick={copyText}
              disabled={!result?.text}
            >
              <span aria-hidden="true">
                {copied ? "✓" : "▢"}
              </span>
              {copied ? "Copied!" : "Copy Text"}
            </button>
          </div>

          <div className="result-body">
            {loading ? (
              <div className="empty-state">
                <div className="spinner" />
                <h3>Extracting your text...</h3>
                <p>
                  Processing your request (up to 60 seconds).
                </p>
              </div>
            ) : result ? (
              <div className="text-content">
                <h3>{result.title || "Webpage content"}</h3>
                <p>{result.text || "No readable text found."}</p>
              </div>
            ) : (
              <div className="empty-state">
                <div className="empty-document">▤</div>
                <h3>Your extracted text will appear here.</h3>
                <p>
                  Enter a URL above and click Extract Text
                  to get started.
                </p>
              </div>
            )}
          </div>
        </section>

        <section className="features" id="features">
          <div className="feature">
            <span className="feature-icon fast-icon">ϟ</span>
            <div>
              <h3>Fast</h3>
              <p>Get text in seconds.</p>
            </div>
          </div>

          <div className="feature">
            <span className="feature-icon clean-icon">▱</span>
            <div>
              <h3>Clean</h3>
              <p>Read without clutter.</p>
            </div>
          </div>

          <div className="feature">
            <span className="feature-icon copy-icon">▢</span>
            <div>
              <h3>Copy</h3>
              <p>Save the text instantly.</p>
            </div>
          </div>
        </section>

        <section className="info-section" id="how-it-works">
          <h2>How it Works</h2>
          <p>
            Paste a public webpage URL, press Enter or click
            Extract Text, then copy the result.
          </p>
        </section>

        <footer id="tech-stack">
          Built with React and Spring Boot
        </footer>
      </main>
    </div>
  );
}

export default App;
