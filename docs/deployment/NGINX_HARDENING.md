# nginx hardening — frontend host

**Audience:** whoever administers the VPS nginx. **Time:** about 15 minutes per environment.
**Order:** staging first, then production after a clean week.

The live frontend is served by the VPS nginx straight from `<DEPLOY_PATH>/frontend/dist`. Today
production sends **no** security headers, no compression and no caching, and `/robots.txt`,
`/.env` and missing assets all return `index.html`. Run the checker to see it:

```bash
scripts/deploy/check-http-headers.sh https://app.axonxmedtech.com     # 16 of 17 checks fail today
```

## What this adds

| Area | Change |
|---|---|
| Compression | gzip for JS, CSS, JSON and SVG (main bundle 298 KB → 99 KB) |
| Caching | `/assets/*` cached for a year (`immutable`, file names are content-hashed); `index.html` and `version.json` always revalidate |
| Security headers | `X-Content-Type-Options`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy`, `Permissions-Policy`, `X-Robots-Tag: noindex`, server version hidden |
| HSTS | Starts at `max-age=300` (5 minutes) and is raised to a year later |
| Content-Security-Policy | Ships **report-only**: it logs violations and blocks nothing; enforce it later |
| Paths | A missing `/assets/*` returns a real 404; dot-files (`.env`, `.git`) return 404 |

The config lives in the repo under `frontend/nginx/`:

- `security-headers.conf`: the headers (included in every location, because nginx drops
  server-level `add_header` in any location that sets its own).
- `csp.conf`: the policy, in one place, with the reason for each source.
- `spa.conf`: gzip, caching and the location blocks.
- `site-example.conf`: the server block, with the per-environment values to set.

The Docker image (`frontend/Dockerfile`) uses the same files.

## Tested before shipping

- **nginx 1.24 (the server's version):** `nginx -t` passes, and `check-http-headers.sh` passes all 17 checks.
- **CSP enforced (not just report-only), in Chromium:**
  - Pages: every login page, the 404 page, all 10 role dashboards with each of 122 sidebar sections, and the phone menu.
  - Result: **0 violations and 0 page errors**.
  - Fonts load.
  - Both print paths work: desktop iframe printing and the iOS print window.
  - An injected inline script is blocked.

## Rollout: staging

1. **Install the snippets** from a checkout of the merged branch:
   ```bash
   sudo mkdir -p /etc/nginx/hms
   sudo cp frontend/nginx/security-headers.conf frontend/nginx/spa.conf frontend/nginx/csp.conf /etc/nginx/hms/
   ```
2. **Back up the site file**, then edit it:
   ```bash
   ls /etc/nginx/sites-enabled/                       # find the staging-app site file
   sudo cp /etc/nginx/sites-available/<site> /root/<site>.bak.$(date +%F)
   sudo nano /etc/nginx/sites-available/<site>
   ```
   In the `server { listen 443 ssl; server_name staging-app.axonxmedtech.com; … }` block:
   - **Keep** `listen`, `server_name`, every `ssl_*` line and `root`.
   - **Delete** the existing `location / { … }` block and any `add_header` lines.
   - **Paste** the lines from `set $hms_api_origin` down to `include hms/spa.conf;` from
     `frontend/nginx/site-example.conf` (staging values are the defaults there).
   - Leave the API site (`staging-api.…`) untouched.
3. **Test and reload.** `reload` keeps existing connections; a bad config is rejected without downtime.
   ```bash
   sudo nginx -t && sudo systemctl reload nginx
   ```
4. **Verify** from any machine:
   ```bash
   scripts/deploy/check-http-headers.sh https://staging-app.axonxmedtech.com
   ```
   Then log in to staging and do the following with the browser console open:
   - open a few screens,
   - print a bill and a prescription,
   - upload a hospital logo.

   Any `Content-Security-Policy` messages are report-only, so nothing breaks. Send them to the dev team.

**Rollback:** `sudo cp /root/<site>.bak.<date> /etc/nginx/sites-available/<site> && sudo nginx -t && sudo systemctl reload nginx`.

## Rollout: production

Do the same with the production values from `site-example.conf`:
- `server_name app.axonxmedtech.com`
- `root /var/www/hms/production/repo_prod/frontend/dist`
- `https://api.axonxmedtech.com` and `wss://api.axonxmedtech.com`

## After a clean week

1. **Enforce CSP** in each site file:
   ```nginx
   set $hms_csp_enforce $hms_csp;
   set $hms_csp_report  "";
   ```
2. **Raise HSTS** to a year: `set $hms_hsts "max-age=31536000";`. Browsers remember this, so
   only do it once HTTPS has been reliable on that host.

Reload after each step (`sudo nginx -t && sudo systemctl reload nginx`) and re-run the checker.

## Changing the policy later

- A new external service (analytics, a CDN, another API) must be added to `frontend/nginx/csp.conf`.
- The policy allows two specific inline scripts by hash. `src/utils/cspHashes.test.js` fails if either one changes, or if a new inline handler appears in a print template. That way a CSP break shows up in CI, not on a user's iPhone.
