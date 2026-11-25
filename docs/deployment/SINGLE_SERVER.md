# Single-server deployment (deliberate)

**Decision (Nov 2026):** the backend runs as **one instance** on one VPS. Do not put a second
instance behind a load balancer until the items below are moved out of process memory. Everything
else is already stateless (JWT logins, database-backed data), so this list is the whole job.

## State held in one server's memory

| What                                                 | Where                                                                       | What breaks with two instances                                                                                             |
| ---------------------------------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Live-update connections (WebSocket "refresh" pushes) | `security/HospitalWebSocketHandler` — `Map<hospitalId, sessions>`           | A change made on instance A only refreshes screens connected to A. Users on B see stale data until they reload.            |
| Rate-limit counters                                  | `filter/RateLimitFilter` — bucket4j buckets in a `ConcurrentHashMap`        | Each instance counts separately, so the real limit becomes _N ×_ the configured one (weaker login brute-force protection). |
| Tenant access cache (plan expired / blocked)         | `service/subscription/TenantAccessService` — 30 s cache                     | A block/expiry evicts only the local cache; other instances keep serving the tenant for up to 30 s.                        |
| Staffing snapshot                                    | `service/hospital/HospitalStaffing` — 30 s cache                            | Same: up to 30 s of stale "who covers reception/OT" on other instances.                                                    |
| Scheduled jobs                                       | `BillingSchedulerService` (midnight IPD day charges), `PlanExpiryScheduler` | Each instance runs them. Day-2+ bed charges are idempotent by line description, but the work and audit noise double.       |

Redis is already in the stack (`RedisConfig`, used for the FAQ / stats caches), which is where
these move.

## Before scaling out

1. **WebSocket fan-out** — publish refresh events to a Redis channel; every instance subscribes and
   pushes to its own sessions. (Or sticky sessions + the Redis channel; the channel is required either way.)
2. **Rate limits** — switch bucket4j to its Redis-backed proxy manager (`bucket4j-redis`); the tier
   logic in `RateLimitFilter` is unchanged.
3. **Caches** — move `TenantAccessService` and `HospitalStaffing` to Spring Cache on Redis, or publish
   an eviction message on the same channel as (1).
4. **Schedulers** — add a lock (e.g. ShedLock on the database) so each job runs on one instance.
5. Load-test with two instances, then enable the load balancer.

Until then: scale **up** (more CPU/RAM on the one VPS), not **out**.
