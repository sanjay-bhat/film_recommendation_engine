# Security Policy

## Supported Versions

| Version | Supported |
|:--------|:---------:|
| Latest on `main` | Yes |
| Feature branches | No |

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it responsibly:

1. **Do not** open a public GitHub issue.
2. Email **sanjaybhat18492@gmail.com** with:
   - A description of the vulnerability
   - Steps to reproduce
   - Any potential impact assessment
3. You will receive acknowledgment within **48 hours**.
4. A fix will be prioritized and released as soon as practical.

## Security Considerations

### Input Validation

All four language implementations (Python, Rust, C#, Go) accept user input via CLI flags (`--movie`, `--id`, `--data-dir`). Each implementation:

- Validates `--id` is within dataset bounds before indexing
- Treats `--movie` as a lookup key matched against existing titles — no string interpolation into queries or shell commands
- Restricts `--data-dir` to filesystem path resolution only — no network fetch, no URL handling

### Data Handling

- The engine operates **entirely offline** — no network calls, no external APIs, no telemetry
- Dataset files (TMDb 5000 CSVs) are read-only; no user data is written to disk
- No authentication, sessions, cookies, or tokens are involved
- No database connections — all data is loaded from flat CSV files into memory

### GitHub Pages Site

The static site in `docs/` is pure HTML/CSS/JS with:

- No form submissions or user input collection
- No cookies or local storage
- External dependency limited to Google Fonts (Orbitron, Share Tech Mono)
- No analytics or tracking scripts

### Dependency Surface

| Language | External Dependencies |
|:---------|:---------------------|
| Python | numpy, pandas, scikit-learn, nltk, thefuzz, python-Levenshtein |
| Rust | None (zero external crates) |
| C# | None (standard .NET 8 libraries only) |
| Go | None (standard library only) |

The Python implementation carries the largest dependency surface. Pin versions in production environments and audit with `pip audit` or `safety check` periodically.

### CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/ci.yml`):

- Runs on `ubuntu-latest` with GitHub-hosted runners
- Uses only official actions (`actions/checkout`, `actions/setup-python`, `actions/setup-go`, `dtolnay/rust-toolchain`, `actions/setup-dotnet`)
- GitHub Pages deployment requires `pages: write` and `id-token: write` permissions, scoped to the deploy job only
- No secrets or API keys are used in the pipeline

### Network Security

Since this is an offline CLI tool with no server component:

- **No open ports** — the engine does not listen on any network interface
- **No outbound connections** — all processing is local (except NLTK's one-time corpus download: `nltk.download('wordnet')`)
- **No serialization/deserialization of untrusted data** — input is limited to the bundled TMDb CSVs and CLI arguments
- If wrapping this engine in a web service, ensure:
  - Rate limiting on the recommendation endpoint
  - Input sanitization on movie title queries
  - CORS headers restricting allowed origins
  - No direct filesystem path exposure via `--data-dir` in a web context
