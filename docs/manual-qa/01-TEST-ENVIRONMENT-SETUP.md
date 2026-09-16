# 01 — TEST ENVIRONMENT SETUP

**Baseline:** `origin/staging` @ `aa143a7`

> **Rule that overrides everything else in this document:** never point a QA environment at a
> production database, and never log into production with a QA account. Every credential in this
> pack is a synthetic value you create yourself. If you are ever unsure whether an environment is
> production, **stop and ask** — do not test.

---

## 1. What you need

| Component | Version        | Where it comes from                                   |
| --------- | -------------- | ----------------------------------------------------- |
| Java      | **17**         | `backend/pom.xml` → `<java.version>17</java.version>` |
| Maven     | 3.8+           | bundled or system                                     |
| Node.js   | 18+            | for the frontend                                      |
| MySQL     | **8.0**        | the app's only supported database                     |
| Redis     | any recent     | optional locally; caching degrades gracefully         |
| Browser   | Chrome or Edge | DevTools instructions in `05` assume Chromium         |

> ⚠️ **Java version matters.** The project targets Java 17. If Maven picks up a newer JDK the
> backend test suite collapses with `Mockito cannot mock this class`. Check with `mvn -v` — it
> reports Maven's _own_ JDK, which can differ from `java -version`. Force it if needed:
>
> - macOS/Linux: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` (macOS) or set it to your JDK 17 path
> - Windows: `set JAVA_HOME=C:\path\to\jdk-17`

---

## 2. Database

```sql
CREATE DATABASE hms_qa CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'hms_qa'@'localhost' IDENTIFIED BY '<pick-your-own-throwaway-password>';
GRANT ALL PRIVILEGES ON hms_qa.* TO 'hms_qa'@'localhost';
FLUSH PRIVILEGES;
```

Then load the canonical schema:

```bash
mysql -u hms_qa -p hms_qa < setup/schema-full.sql
```

Then create the first Super Admin (this is the **only** account you cannot create through the UI):

```bash
mysql -u hms_qa -p hms_qa < setup/setup-super-admin.sql
```

> Open `setup/setup-super-admin.sql` and read it before running it — it contains the seeded
> Super Admin email. **Change the password immediately after first login** (see `TC-AUTH-004`).

**Schema drift:** `DatabaseMigrationRunner` runs idempotent patches on every startup, and
Hibernate `ddl-auto=update` also applies entity changes. A fresh QA database therefore converges
even if `schema-full.sql` lags slightly. If the backend logs `DB migration skipped (...)` warnings
at startup, capture them — they are not always harmless.

---

## 3. Backend

Create `backend/.env` from `backend/.env.example`:

```
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/hms_qa?useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=hms_qa
SPRING_DATASOURCE_PASSWORD=<your throwaway password>
JWT_SECRET=<a long random string you generate — never reuse a production secret>
FRONTEND_URL=http://localhost:5173
PORT=8080
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379
```

Run:

```bash
cd backend
mvn spring-boot:run
```

Health check — this endpoint is public and needs no token:

```bash
curl -i http://localhost:8080/api/public/health
```

Expect **HTTP 200**.

---

## 4. Frontend

Create `frontend/.env`:

```
VITE_API_BASE_URL=http://localhost:8080
```

Run:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

---

## 5. The three login portals

There is no single login page. The portal you use determines the tenant context your session
starts in, and it is remembered for session-expiry redirects.

| Portal   | URL               | Who logs in here          |
| -------- | ----------------- | ------------------------- |
| Platform | `/platform/login` | **SUPER_ADMIN only**      |
| Hospital | `/login/hospital` | all HOSPITAL-tenant users |
| Clinic   | `/login/clinic`   | all CLINIC-tenant users   |
| Pharmacy | `/login/pharmacy` | all PHARMACY-tenant users |

`/login` redirects to `/login/hospital`.

**Important and frequently misreported:** a CLINIC user logs in at `/login/clinic` and then lands
on **`/hospital/admin`** (or `/hospital/receptionist`, etc.). Clinic shares the Hospital frontend
by design. **The URL saying "hospital" for a clinic user is not a bug.**

---

## 6. Where sessions live

The JWT is stored in **`sessionStorage`**, not `localStorage` or a cookie.

This gives you three behaviours you must test rather than assume:

- **Each browser tab is its own session.** You can be Hospital A Receptionist in tab 1 and
  Hospital B Receptionist in tab 2 at the same time. This is extremely useful for isolation
  testing and is exploited throughout `cross-tenant/TENANT-ISOLATION.md`.
- **Closing a tab ends that session.** Re-opening requires a fresh login.
- **A 401 from any API call clears the token and redirects to the login page** (Axios response
  interceptor).

---

## 7. Evidence capture

For every executed case, capture enough that a developer never has to ask you a question:

- A screenshot of the screen **including the URL bar and the visible role/tenant**.
- For API cases: the full request (method, URL, headers minus the token value, body) and the
  full response (status + body). See `05-API-TEST-TRACK.md` §7.
- The **timestamp** and which **tenant + role** you were logged in as.
- Backend console output if the request produced a 500.

> **Never paste a real JWT, password, or connection string into a bug report, a screenshot, or a
> chat message.** Redact the token as `Bearer <redacted>`.

---

## 8. Resetting between test runs

The cleanest reset is to drop and rebuild:

```sql
DROP DATABASE hms_qa;
CREATE DATABASE hms_qa CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

then re-run §2 and re-execute `02-TEST-DATA-SETUP.md`.

**Do not** reset by deleting rows through the UI — soft delete (`is_active = 0`) means deleted
records are still present and still affect duplicate detection and uniqueness behaviour. Several
cases in this pack depend on that.
