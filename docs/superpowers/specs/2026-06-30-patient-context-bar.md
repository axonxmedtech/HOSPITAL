# Patient Context Bar — Design Spec (D2)

**Date:** 2026-06-30  
**Scope:** Frontend only — `IpdDetails.jsx`, inline JSX, no new files

---

## Goal

Surface the most critical patient context (allergies, EWS score, pending labs, unacknowledged CDSS alerts) in a slim horizontal strip at the top of the IPD patient workspace — visible in the first 5 seconds without scrolling.

---

## 1. Placement

Insert the context bar between the Lock Banner block and the main content grid in `IpdDetails.jsx`:

```
PageHeader (line 597)         ← unchanged
Lock Banner (lines 599–609)   ← unchanged
[Patient Context Bar]         ← NEW — inserted here
Main content grid (line 611)  ← unchanged
```

---

## 2. Render Condition

The bar renders only when `smartSummary` is loaded AND there is at least one visible item:

```jsx
{smartSummary && (
    (smartSummary.allergies?.length > 0 ||
     ewsResult ||
     smartSummary.pendingLabTests?.length > 0 ||
     smartSummary.unacknowledgedAlerts?.length > 0)
) && (
    <div className="mt-3 flex flex-wrap items-center gap-2 px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl">
        ...
    </div>
)}
```

- If `smartSummary` is `null` (still loading): bar is invisible — no height, no flash.
- If `smartSummary` loads with no relevant data (no allergies, normal EWS, 0 pending, 0 alerts): bar is hidden.

---

## 3. Content Items (left to right)

### 3a. Allergy badges
Source: `smartSummary.allergies` (array of strings)

- One red pill badge per allergy
- Max 3 shown inline
- If more than 3: show "+N more" badge in same red style
- Hidden if `smartSummary.allergies.length === 0`

```jsx
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
```

### 3b. Separator
A `|` divider between sections (only shown between visible items):
```jsx
<span className="text-gray-300 text-xs select-none">|</span>
```
Use only between sections that are both visible. Simplest implementation: always render separators and let the flex layout handle visual spacing.

### 3c. EWS score badge
Source: `ewsResult` (object with `totalScore`, `severity`)

- Hidden if `ewsResult` is null
- Color: `bg-green-50 text-green-700 border-green-200` for NORMAL, `bg-orange-50 text-orange-700 border-orange-200` for MEDIUM, `bg-red-50 text-red-700 border-red-200` for HIGH

```jsx
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
```

### 3d. Pending labs count
Source: `smartSummary.pendingLabTests?.length`

- Hidden if 0 or undefined
- Amber color

```jsx
{(smartSummary.pendingLabTests?.length ?? 0) > 0 && (
    <span className="text-xs px-2.5 py-0.5 bg-amber-50 text-amber-700 border border-amber-200 rounded-full font-medium">
        🧪 {smartSummary.pendingLabTests.length} pending lab{smartSummary.pendingLabTests.length > 1 ? 's' : ''}
    </span>
)}
```

### 3e. Unacknowledged CDSS alerts count
Source: `smartSummary.unacknowledgedAlerts?.length`

- Hidden if 0 or undefined
- Red color

```jsx
{(smartSummary.unacknowledgedAlerts?.length ?? 0) > 0 && (
    <span className="text-xs px-2.5 py-0.5 bg-red-50 text-red-700 border border-red-200 rounded-full font-semibold">
        🔔 {smartSummary.unacknowledgedAlerts.length} alert{smartSummary.unacknowledgedAlerts.length > 1 ? 's' : ''}
    </span>
)}
```

---

## 4. Architecture

| Constraint | Detail |
|---|---|
| Files changed | `frontend/src/pages/hospital/IpdDetails.jsx` only |
| New state variables | None |
| New API calls | None — reads `smartSummary` and `ewsResult` already in state |
| New components | None — inline JSX |
| Existing sidebar | Unchanged — Clinical Summary sidebar remains as the detailed view |
| Placement | After Lock Banner block (line ~609), before main grid (line 611) |

The bar is purely additive — it shows nothing while loading and disappears entirely when there is no relevant data. Removing it would require only deleting ~30 lines with no other impact.
