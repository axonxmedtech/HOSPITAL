# Patient Context Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a slim horizontal context strip to the top of the IPD patient workspace that shows allergies, EWS score, pending labs, and unacknowledged CDSS alerts in the first 5 seconds — using data already in state, with zero new API calls.

**Architecture:** Single file change — `frontend/src/pages/hospital/IpdDetails.jsx`. Insert ~30 lines of inline JSX between the existing Lock Banner block (around line 609) and the main content grid (line 611). Reads from `smartSummary` and `ewsResult` already in component state (fetched by Phase B CDSS logic). Renders nothing while loading, hides entirely when no relevant data.

**Tech Stack:** React 18, Tailwind CSS, existing `smartSummary` + `ewsResult` state

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/pages/hospital/IpdDetails.jsx` |

---

### Task 1: Patient Context Bar

**Files:**
- Modify: `frontend/src/pages/hospital/IpdDetails.jsx`

**Context:**
The file has this structure near the top of the rendered JSX (around lines 597–611):

```jsx
<PageHeader title={`IPD ${data.ipdNumber || ''}`} subtitle={`...`} />

{/* Lock Banner — shown when patient is discharged or archived */}
{(data.isArchived || data.status === 'DISCHARGED') && (
    <div className={`mt-3 flex items-center gap-3 ...`}>
        ...
    </div>
)}

<div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">   ← main content grid
```

The context bar goes between the Lock Banner closing `)}` and the main content grid `<div>`.

Existing state variables available (already in component):
- `smartSummary` — object with `.allergies`, `.pendingLabTests`, `.unacknowledgedAlerts` (or `null` while loading)
- `ewsResult` — object with `.totalScore`, `.severity` (or `null` if no vitals)

- [ ] **Step 1: Read the file**

Open `frontend/src/pages/hospital/IpdDetails.jsx`. Confirm the exact location:
- Find `<PageHeader title={\`IPD` — note the line number
- Find the Lock Banner block closing `)}` — note the line number
- Find `<div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">` — note the line number

The context bar inserts between the Lock Banner `)}` and that grid `<div>`.

- [ ] **Step 2: Insert the patient context bar**

Find this exact text (the closing of the Lock Banner block):

```jsx
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
```

Replace it with:

```jsx
            )}

            {/* Patient Context Bar — first 5 seconds: allergies, EWS, pending labs, alerts */}
            {smartSummary && (
                (smartSummary.allergies?.length > 0 ||
                 ewsResult ||
                 (smartSummary.pendingLabTests?.length ?? 0) > 0 ||
                 (smartSummary.unacknowledgedAlerts?.length ?? 0) > 0)
            ) && (
                <div className="mt-3 flex flex-wrap items-center gap-2 px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl">
                    {/* Allergies */}
                    {smartSummary.allergies?.length > 0 && (
                        <div className="flex items-center gap-1.5">
                            <span className="text-xs font-semibold text-red-600">⚠️ Allergies:</span>
                            {smartSummary.allergies.slice(0, 3).map((a, i) => (
                                <span key={i} className="text-xs px-2 py-0.5 bg-red-50 text-red-700 border border-red-200 rounded-full font-medium">
                                    {a}
                                </span>
                            ))}
                            {smartSummary.allergies.length > 3 && (
                                <span className="text-xs px-2 py-0.5 bg-red-50 text-red-600 border border-red-200 rounded-full">
                                    +{smartSummary.allergies.length - 3} more
                                </span>
                            )}
                        </div>
                    )}

                    {/* EWS Score */}
                    {ewsResult && (
                        <span className={`text-xs px-2.5 py-0.5 rounded-full font-semibold border ${
                            ewsResult.severity === 'HIGH' ? 'bg-red-50 text-red-700 border-red-200' :
                            ewsResult.severity === 'MEDIUM' ? 'bg-orange-50 text-orange-700 border-orange-200' :
                            'bg-green-50 text-green-700 border-green-200'
                        }`}>
                            EWS {ewsResult.totalScore ?? 0}
                            {ewsResult.severity === 'HIGH' && ' 🔴'}
                            {ewsResult.severity === 'MEDIUM' && ' ⚠️'}
                            {ewsResult.severity === 'NORMAL' && ' 🟢'}
                        </span>
                    )}

                    {/* Pending Labs */}
                    {(smartSummary.pendingLabTests?.length ?? 0) > 0 && (
                        <span className="text-xs px-2.5 py-0.5 bg-amber-50 text-amber-700 border border-amber-200 rounded-full font-medium">
                            🧪 {smartSummary.pendingLabTests.length} pending lab{smartSummary.pendingLabTests.length > 1 ? 's' : ''}
                        </span>
                    )}

                    {/* Unacknowledged CDSS Alerts */}
                    {(smartSummary.unacknowledgedAlerts?.length ?? 0) > 0 && (
                        <span className="text-xs px-2.5 py-0.5 bg-red-50 text-red-700 border border-red-200 rounded-full font-semibold">
                            🔔 {smartSummary.unacknowledgedAlerts.length} alert{smartSummary.unacknowledgedAlerts.length > 1 ? 's' : ''}
                        </span>
                    )}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
```

- [ ] **Step 3: Build and verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` with no errors.

- [ ] **Step 4: Self-review checklist**

Before committing, verify:
- [ ] Bar is inside `IpdDetails.jsx` between the Lock Banner `)}` and the main grid `<div>`
- [ ] Renders only when `smartSummary` is not null AND has relevant data
- [ ] Allergy section: max 3 badges + "+N more"
- [ ] EWS section: color-coded (red/orange/green), hidden when `ewsResult` is null
- [ ] Pending labs: amber badge, hidden when 0
- [ ] CDSS alerts: red badge, hidden when 0
- [ ] No new state variables added
- [ ] No new imports added
- [ ] Lock Banner and main grid untouched

- [ ] **Step 5: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/IpdDetails.jsx && git commit -m "feat(ipd): patient context bar — allergies, EWS, pending labs and alerts visible on load"
```

---

## Self-Review

**Spec coverage:**
- ✅ Placement: after Lock Banner, before main grid — Task 1 Step 2
- ✅ Render condition: `smartSummary` not null AND at least one section has data — Task 1 Step 2
- ✅ Allergy badges (max 3 + overflow) — Task 1 Step 2
- ✅ EWS color coding (HIGH=red, MEDIUM=orange, NORMAL=green) — Task 1 Step 2
- ✅ EWS hidden when `ewsResult` is null — Task 1 Step 2
- ✅ Pending labs count (amber, hidden when 0) — Task 1 Step 2
- ✅ CDSS alerts count (red, hidden when 0) — Task 1 Step 2
- ✅ No new state, no new API calls, no new imports — Task 1 Step 4

**Placeholder scan:** None found.

**Type consistency:** `smartSummary.allergies` is `string[]`, `.pendingLabTests` is `string[]`, `.unacknowledgedAlerts` is array — optional chaining + `?? 0` guards handle undefined safely throughout.
