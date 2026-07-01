# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Design philosophy

Before writing a UX spec or designing a new screen, read
[docs/superpowers/product-philosophy.md](docs/superpowers/product-philosophy.md) —
the Strynkix product principles (problem-first design, mental models borrowed
from real life, cognitive-load rules, tasks-over-modules direction) that guide
this codebase's UX and architecture decisions.

## Commands

### Backend (Spring Boot / Maven)
```bash
cd backend
mvn spring-boot:run          # Start dev server (port 8080)
mvn clean package -DskipTests # Build JAR
mvn test                     # Run tests
```

### Frontend (React / Vite)
```bash
cd frontend
npm install                  # Install dependencies
npm run dev                  # Start dev server (port 5173)
npm run build                # Production build (runs tsc then vite build)
```

### Database
```bash
# Initial setup
CREATE DATABASE hospital_management;
# Then run: setup/setup-super-admin.sql
# Optional test data: setup/test-data-doctors.sql
```

## Architecture

This is a **multi-tenant SaaS Hospital Management System** — a monorepo with a Spring Boot backend and a React frontend.

### Tenant Model
Two tiers:
- **Platform level** — Super Admin manages hospitals (onboarding, billing)
- **Hospital level** — Hospital Admin manages their own staff and patients

All hospital-scoped entities carry a `hospital_id` foreign key. The backend extracts `hospitalId` from the JWT claims via `SecurityHelper.getCurrentHospitalId()` and enforces tenant isolation in every service method.

### Backend (`backend/src/main/java/com/hms/`)

| Package | Purpose |
|---|---|
| `entity/` | JPA entities (Patient, Doctor, Appointment, Opd, Ipd, Prescription, Medicine, Billing, …) |
| `repository/` | Spring Data JPA repositories |
| `service/hospital/` | Business logic for hospital-level operations |
| `service/platform/` | Platform (Super Admin) operations |
| `controller/hospital/` | REST handlers under `/hospital/**` |
| `controller/platform/` | REST handlers under `/platform/**` |
| `controller/publicapi/` | Unauthenticated endpoints (`/api/public/**`) |
| `security/` | JWT filter (`JwtAuthenticationFilter`), `JwtUtil`, `SecurityHelper` |
| `config/` | Spring Security config, Redis config, WebSocket config, CORS |
| `dto/` | Request/response DTOs |

**API URL namespaces:**
- `/platform/**` — Super Admin only
- `/hospital/**` — All hospital roles (ADMIN, DOCTOR, RECEPTIONIST, PHARMACIST)
- `/api/pharmacy/**` — Pharmacy module
- `/ws/**` — WebSocket

**Roles:** `SUPER_ADMIN`, `HOSPITAL_ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PHARMACIST`

Special flag: `isSingleDoctor` on a `HOSPITAL_ADMIN` user lets that admin also act as the sole doctor (single-doctor clinic mode).

### Frontend (`frontend/src/`)

| Directory | Purpose |
|---|---|
| `pages/hospital/` | Per-role dashboards: `HospitalAdminDashboard`, `DoctorDashboard`, `ReceptionistDashboard`, `PharmacistDashboard` |
| `pages/platform/` | Super Admin UI |
| `components/` | Shared UI: `DataTable`, `ActionMenu`, `StatusBadge`, `ConfirmationModal`, `Sidebar`, `Navbar`, `PageHeader`, … |
| `services/` | Axios wrappers — `apiService.js` (interceptors), `hospitalService.js`, `authService.js` |
| `context/` | `ToastContext` (global toast notifications) |
| `hooks/` | `useWebSocket`, `useModule` |

**Routing:** React Router 7. `ProtectedRoute` guards all non-login pages. After login the user is redirected to their role's dashboard.

**Auth flow:** JWT stored in `sessionStorage` (tab-isolated). Axios request interceptor injects `Authorization: Bearer <token>`. The 401 response interceptor clears the token and redirects to `/login`.

**Table components pattern:** Large dashboard files contain co-located table components at the bottom (`PatientsTable`, `DoctorsTable`, `AppointmentsTable`, etc.) using `@tanstack/react-table` with a `createColumnHelper`. Actions use the shared `ActionMenu` component (three-dot dropdown). The `isAdmin` prop controls whether the Actions column is rendered.

### Database schema source of truth
`setup/schema-full.sql` is the canonical schema. Entity classes in `entity/` mirror it. When adding columns, update both the SQL (provide the ALTER query) and the JPA entity.

### PDF generation
`PdfService` uses OpenPDF + Thymeleaf templates under `src/main/resources/templates/` (e.g. `case-paper.html`).

### WebSocket
Real-time dashboard updates. Config in `com.hms.config.WebSocketConfig`. Secured with JWT. Frontend hook: `useWebSocket`.

## Environment

**Backend** reads from `.env` (loaded via Spring's property source):
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `FRONTEND_URL` (CORS origin)
- `SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`

**Frontend** reads from `.env`:
- `VITE_API_BASE_URL` (defaults to `http://localhost:8080`)
- `VITE_CLOUDINARY_CLOUD_NAME`, `VITE_CLOUDINARY_UPLOAD_PRESET` (logo uploads)
