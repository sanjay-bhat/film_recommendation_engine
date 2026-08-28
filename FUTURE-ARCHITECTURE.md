# Future Architecture Roadmap

> Playbook artifact: https://claude.ai/code/artifact/0e38f3d3-2a07-4215-8707-aa37e5009b61
>
> Research date: 2026-08-27
>
> Sources: Netflix, Prime Video, YouTube, Spotify engineering practices mapped against current Film Recommend architecture.

---

## Phase 1 — Immediate (This Week)

### 1.1 Extract Swift Package for shared iOS/iPad/tvOS code

**Inspired by:** Netflix (Kotlin Multiplatform for shared business logic)

`SupabaseClient.swift`, `SpeechManager.swift`, `RecommendationEngine.swift`, and shared models are copy-pasted across iOS, iPad, and Apple TV targets. Create a local Swift Package (`FilmRecommendCore`) and import it in all three Apple targets.

- Eliminates 3 copies of identical code
- Single place to update Supabase URL/key, speech config, model types

### 1.2 Extract Kotlin shared module for Android/Tablet/GoogleTV

Same pattern, Kotlin side. Create a `:shared` Gradle module with `SupabaseClient`, data models, and API logic. All three Android targets depend on it.

### 1.3 Add fuzzy search to the web frontend

**Inspired by:** Spotify (Levenshtein + Damerau-Levenshtein edit distance, Soundex/Metaphone)

Current web search is exact substring only — "Incpetion" returns nothing. Add `fuse.js` (3.5KB gzipped) for typo tolerance. Especially important since voice search can produce phonetic near-misses.

The Python CLI already uses `thefuzz` + `python-Levenshtein` — this brings parity to the frontend.

### 1.4 Virtual scrolling in the coverflow

**Inspired by:** Netflix (row recycling, only visible items + buffer rendered), YouTube (DOM recycling)

Current coverflow renders all 6,700+ slides into the DOM. Implement intersection-observer-based recycling — render ~15 visible slides, recycle off-screen nodes. Immediate DOM reduction from thousands to dozens of elements.

### 1.5 Remove `unsafe-inline` from CSP

**Inspired by:** Netflix (strict CSP via Zuul gateway), Prime Video (WAF)

Move inline scripts and styles to external files or use nonce-based CSP. Prevents XSS even if sanitization has a gap. Currently `unsafe-inline` is allowed for both scripts and styles.

### 1.6 Add TTL to the in-memory recCache

Current `recCache` Map in JS has no eviction. Store entries as `{ data, timestamp }` and invalidate after 1 hour. Prevents stale recommendations after ETL updates and bounds memory growth.

---

## Phase 2 — Near-Term (Next 2 Weeks)

### 2.1 Rate limiting via Supabase Edge Functions

**Inspired by:** Netflix (Zuul per-user/per-service quotas), Prime Video (WAF + Cognito), YouTube (API quota units)

Currently zero rate limiting anywhere. Create an Edge Function proxy that checks IP-based request counts before forwarding to PostgREST. Start simple: 60 req/min per IP.

### 2.2 Supabase column selection on all queries

**Inspired by:** YouTube Data API v3 `fields` parameter for partial responses

Currently all queries use `select('*')`. Use `.select('id, title, poster_path, vote_average')` to return only needed fields. Reduces response size by 60-80% for browse queries.

### 2.3 Cursor-based pagination for browse views

**Inspired by:** YouTube (token-based `nextPageToken`/`prevPageToken`), Spotify (offset/cursor)

Currently loads all 6,700 titles in one request. Use Supabase `.range(from, to)` to paginate in chunks of 50. Combined with virtual scrolling, this is how Netflix handles a catalog of thousands.

### 2.4 Postgres full-text search with GIN-indexed tsvector

**Inspired by:** Spotify (inverted indexes + trie structures), YouTube (Vitess query routing)

Stop fetching all titles to the client for search. Add a `tsvector` column to the movies table with a GIN index. Query with `to_tsquery` via Supabase RPC. Scales to 1M+ titles.

### 2.5 Pre-compute 9-signal blended recommendations

**Inspired by:** Netflix (80% of watched content from pre-computed recs), Spotify (Discover Weekly pre-generated weekly)

Current Supabase `get_recommendations` RPC uses only 1 signal (embedding cosine distance). The Python CLI has the full 9-signal blend (Content 25%, Collab 15%, Genre 15%, Plot 10%, Genome 8%, Actors 8%, Director 7%, Popularity 7%, Year 5%).

Pre-compute blended scores during ETL and store top-N recommendations per movie in a `movie_recommendations` lookup table. The RPC becomes a simple `SELECT` instead of a cosine distance computation.

### 2.6 Switch poster URLs to WebP

**Inspired by:** Netflix (AVIF pioneer, WebP fallback), Prime Video (CloudFront edge auto-format via Accept header)

TMDB CDN already supports WebP. ~30% smaller payloads with no quality loss.

### 2.7 Add Cache-Control headers

**Inspired by:** YouTube (aggressive edge caching), Prime Video (per-content-type TTLs)

Supabase responses currently have no cache headers. Wrap frequently-hit queries in Edge Functions that set `Cache-Control: public, max-age=3600, stale-while-revalidate=86400`.

---

## Phase 3 — Stretch (1M+ Scale)

### 3.1 Supabase Auth with PKCE for personalization

**Inspired by:** Spotify (OAuth 2.0 + PKCE eliminates client secret storage on devices)

When adding user-specific features (watchlists, ratings, personal recommendations), use Supabase Auth with PKCE flow. RLS policies tied to `auth.uid()`.

### 3.2 Anonymous interaction tracking

**Inspired by:** Netflix (contextual bandits), YouTube (engagement metrics, return rate signals)

Log which movie cards users click: a `click_events` table with `movie_id + timestamp + anonymous session_id`. No auth needed. Feeds popularity-by-interaction data back into the scoring pipeline.

### 3.3 Server-driven UI for browse layout

**Inspired by:** Netflix (SDUI for lifecycle screens), Prime Video (backend controls layout per device class), Spotify (server-driven home screen section ordering)

Store section ordering (trending, recommended, genre rows) in Supabase. Backend controls layout. Enables A/B testing layouts and adding new sections without app store updates.

### 3.4 Phonetic search for voice input

**Inspired by:** Spotify (Soundex + Metaphone for sound-alike queries)

Install `pg_trgm` + `fuzzystrmatch` Postgres extensions (both available on Supabase) for trigram similarity and Soundex matching. "The Dark Night" should find "The Dark Knight."

### 3.5 Concurrent ETL in Go/Rust

**Inspired by:** Netflix (parallel per-title encoding), Spotify (20,000 data jobs on Cloud Dataflow)

Rewrite `fetch_tmdb_catalog.py` in Go with goroutine-based concurrent API fetching for 10-50x throughput at 1M+ titles.

### 3.6 ANN index with Voyager/Annoy

**Inspired by:** Spotify (open-sourced Voyager for HNSW, Annoy for tree-based ANN)

At 1M+ titles, pgvector cosine search will slow down. Pre-build an ANN index during ETL — Voyager handles 10M+ vectors in memory.

### 3.7 LQIP (Low-Quality Image Placeholders)

**Inspired by:** Prime Video (tiny blurred placeholder before full poster)

Generate 20px-wide base64 thumbnails during ETL, embed inline. Perceived load time drops dramatically on slow connections.

---

## Oracle Cloud Free Tier Deployment

### Infrastructure Overview

Target: Oracle Cloud Always Free tier for self-hosted Postgres + ETL pipeline.

**Instance strategy:**
- **VM.Standard.A1.Flex** (4 Arm cores, 24 GB RAM) — primary: runs Postgres + pgvector + ETL
- **VM.Standard.E2.1.Micro** (1/8 OCPU, 1 GB RAM) — fallback/proxy: Nginx + Cloudflare tunnel

If only the Micro is available, run ETL locally and use the instance only for serving pre-computed results.

### New files needed

```
infra/
├── provision.sh          — Oracle instance provisioning with A1 retry + AMD fallback
├── setup-instance.sh     — First-boot: 4GB swap, Docker, firewall, Postgres tuning
├── crontab.prod          — Scheduled ETL (keeps instance alive) + health ping + backups
├── healthcheck.sh        — External uptime monitor (runs from GitHub Actions)
├── backup.sh             — Nightly pg_dump to Oracle Object Storage (10GB free)
└── nginx.conf            — Gzip compression + cache headers for self-hosted mode
docker-compose.yml        — Full stack: pgvector/pgvector:pg16 + Nginx + ETL runner
Dockerfile.etl            — ETL container with all Python dependencies
.github/workflows/
└── healthcheck.yml       — Scheduled uptime checks every 30 minutes
```

### 8 Pain Points and Solutions

| # | Pain Point | Solution |
|---|---|---|
| 1 | **Capacity hell** (A1 always "out of capacity") | `infra/provision.sh` — retry loop every 60s with AMD micro fallback |
| 2 | **Idle reclamation** (instance stopped after 7 days idle) | Scheduled ETL cron keeps the box active with real work; health ping as backup |
| 3 | **Region lock-in** (Home Region permanent) | `docker-compose.yml` — fully portable stack, zero Oracle-specific services |
| 4 | **1 GB RAM** (AMD micro only) | `setup-instance.sh` — 4GB swap + tuned Postgres (128MB shared_buffers, 20 max connections) |
| 5 | **Two-layer firewall** (VCN + OS iptables) | `setup-instance.sh` handles both; Cloudflare Tunnel sidesteps it entirely |
| 6 | **No SLA/support** | `healthcheck.yml` — GitHub Actions pings every 30min, Discord webhook alerts |
| 7 | **Egress limits** (10TB/month) | `nginx.conf` — gzip + cache headers; Cloudflare free tier absorbs 80-90% egress |
| 8 | **Autonomous DB cold starts** (15-30s after idle) | Skip it — self-host Postgres via Docker, no cold starts, no 20GB limit |

### VM Comparison

| | E2.1.Micro | A1 Flex |
|---|---|---|
| CPU | 1/8 OCPU (AMD) | 4 OCPU (Arm) |
| RAM | 1 GB | 24 GB |
| Can run ETL? | No (OOM on embeddings/SVD) | Yes — full pipeline in memory |
| Can run Postgres? | Yes, but tight (128MB buffers) | Yes — 4GB shared_buffers, room for pgvector |
| 1M+ titles? | Impossible | Feasible (1M × 384 embedding = ~1.5GB) |
| Best role | Nginx proxy / tunnel endpoint | Postgres + ETL workhorse |

### Micro-only architecture (if A1 unavailable)

If only the E2.1.Micro is available:
1. Run ETL locally on Mac, push pre-computed results to the instance
2. Store top-N recommendations as a flat lookup table (simple `SELECT`, no pgvector computation)
3. Instance only serves Nginx + static files + lightweight Postgres reads
4. Keep Supabase as primary until A1 capacity frees up

---

## Current State Reference

### What we already do well
- 9-signal recommendation system (more sophisticated than many production recommenders)
- Service worker with stale-while-revalidate
- Skeleton shimmer loading for coverflow and mobile cards
- Input sanitization (`sanitizeInput()`, `escapeHtml()`, prototype pollution guard)
- CSP headers (`frame-src 'none'`, `frame-ancestors 'none'`)
- Voice search across 6 platforms with on-device recognition
- Preconnect hints for Supabase, TMDB, Google Fonts
- Responsive poster loading with `srcset`/`sizes` + lazy loading
- Static JSON fallback for offline/GitHub Pages mode
- Service role key passed via CLI args (not hardcoded) for migration scripts

### Platform techniques worth studying further

| Platform | Open-Source Tool | What it does |
|---|---|---|
| Netflix | Zuul 2 | API gateway / edge proxy |
| Netflix | Eureka | Service discovery |
| Netflix | Chaos Monkey | Resilience testing via random failure injection |
| Netflix | DGS Framework | Federated GraphQL for Spring Boot |
| Netflix | Spinnaker | Multi-cloud continuous delivery |
| Netflix | Hollow | In-memory dataset dissemination |
| Spotify | Backstage | Developer portal for service catalogs + CI/CD |
| Spotify | Voyager | HNSW-based nearest-neighbor search |
| Spotify | Annoy | Tree-based approximate nearest neighbors |
| Spotify | Luigi | Python batch pipeline orchestration |
| Spotify | Confidence | Feature flags + A/B testing |
| YouTube | Vitess | Transparent MySQL sharding |
