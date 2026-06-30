# Keyboard Shortcuts — Design Spec (D4)

**Date:** 2026-06-30
**Scope:** Frontend only — 4 files changed, no new API calls, no backend changes

---

## Goal

Add Ctrl+D / Ctrl+L / Ctrl+R keyboard shortcuts that let the doctor jump directly to clinical fields without touching the mouse — scoped to the contexts where each action is meaningful.

---

## 1. Shortcut Map

| Shortcut | ConsultationModal (OPD) | IpdDetails (IPD) |
|----------|------------------------|-----------------|
| **Ctrl+D** | Switch to Clinical tab → focus Diagnosis textarea | Not implemented |
| **Ctrl+L** | Switch to Clinical tab → tick "Order Lab Tests" → scroll into view | Open LabResultsPanel "New Lab Order" form |
| **Ctrl+R** | Not implemented | Open RadiologyResultsPanel "New Radiology Order" form |

Browser defaults (bookmark, address bar, refresh) are suppressed via `event.preventDefault()` only when the relevant component intercepts the event.

---

## 2. ConsultationModal Changes (`frontend/src/components/ConsultationModal.jsx`)

### 2a. Keydown listener

A single `useEffect` registers and tears down the listener based on `isOpen`:

```js
useEffect(() => {
  if (!isOpen) return;
  const handleKeyDown = (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
      e.preventDefault();
      setActiveTab('clinical');
      setTimeout(() => {
        document.getElementById('consultation-diagnosis')?.focus();
      }, 0);
    }
    if ((e.ctrlKey || e.metaKey) && e.key === 'l') {
      e.preventDefault();
      setActiveTab('clinical');
      setFormData(prev => ({ ...prev, labRequired: true }));
      setTimeout(() => {
        document.getElementById('consultation-lab-section')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 0);
    }
  };
  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, [isOpen]);
```

`setTimeout(..., 0)` defers the focus/scroll until after the tab-switch state update renders.

### 2b. DOM anchors

Add `id="consultation-diagnosis"` to the `<textarea>` rendered inside the Diagnosis `CharCountInput`. Since `CharCountInput` renders a `<textarea>`, wrap it in a `<div>` and query the textarea:

```jsx
<div id="consultation-diagnosis-anchor">
  <CharCountInput
    label={
      <span className="flex items-center gap-2">
        Diagnosis
        <span className="text-xs font-mono text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">Ctrl+D</span>
      </span>
    }
    textarea
    rows={3}
    value={formData.diagnosis}
    onChange={(e) => handleChange('diagnosis', e.target.value)}
    maxLength={500}
    placeholder="Enter diagnosis..."
  />
</div>
```

And in the keydown handler, target:
```js
document.getElementById('consultation-diagnosis-anchor')?.querySelector('textarea')?.focus();
```

Add `id="consultation-lab-section"` to the lab checkbox `<div>`:
```jsx
<div id="consultation-lab-section" className="mt-4">
  <div className="flex items-center gap-3">
    <input id="lab-checkbox" type="checkbox" ... />
    <label htmlFor="lab-checkbox" className="text-sm font-medium text-gray-700">
      Order Lab Tests
      <span className="ml-2 text-xs font-mono text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">Ctrl+L</span>
    </label>
  </div>
  ...
</div>
```

### 2c. Shortcut hint badges

The `Ctrl+D` and `Ctrl+L` hint badges are inline `<span>` elements with `text-xs font-mono text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded` styling, placed inside the respective labels. They are visible only when the Clinical tab is active. No separate tooltip component needed.

---

## 3. LabResultsPanel Changes (`frontend/src/components/lab/LabResultsPanel.jsx`)

### 3a. New prop

```js
export default function LabResultsPanel({ ipdAdmissionId, patientId, canOrder = false, openTrigger = 0 }) {
```

### 3b. Trigger effect

```js
useEffect(() => {
  if (openTrigger > 0 && canOrder) setShowOrderForm(true);
}, [openTrigger, canOrder]);
```

When `openTrigger` increments, the order form opens (if the user has `canOrder` permission). If the form is already open, this is idempotent (setting `true` again has no effect). No other changes to the component.

---

## 4. RadiologyResultsPanel Changes (`frontend/src/components/radiology/RadiologyResultsPanel.jsx`)

Identical pattern to `LabResultsPanel`:

### 4a. New prop

```js
export default function RadiologyResultsPanel({ ipdAdmissionId, patientId, canOrder = false, openTrigger = 0 }) {
```

### 4b. Trigger effect

```js
useEffect(() => {
  if (openTrigger > 0 && canOrder) setShowOrderForm(true);
}, [openTrigger, canOrder]);
```

---

## 5. IpdDetails Changes (`frontend/src/pages/hospital/IpdDetails.jsx`)

### 5a. New state

```js
const [labOpenTrigger, setLabOpenTrigger] = useState(0);
const [radOpenTrigger, setRadOpenTrigger] = useState(0);
```

### 5b. Keydown listener

```js
useEffect(() => {
  const handleKeyDown = (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'l') {
      e.preventDefault();
      setLabOpenTrigger(t => t + 1);
    }
    if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
      e.preventDefault();
      setRadOpenTrigger(t => t + 1);
    }
  };
  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, []);
```

No `isOpen` guard needed — these shortcuts are always active on the IpdDetails page, which is a dedicated route.

### 5c. Pass props to panels

```jsx
<LabResultsPanel
  ipdAdmissionId={admission?.id}
  patientId={admission?.patient?.id}
  canOrder={canOrder}
  openTrigger={labOpenTrigger}
/>

<RadiologyResultsPanel
  ipdAdmissionId={admission?.id}
  patientId={admission?.patient?.id}
  canOrder={canOrder}
  openTrigger={radOpenTrigger}
/>
```

The existing `canOrder` prop already controls whether the form button is shown — the shortcut reuses the same gate.

---

## 6. Architecture Summary

| File | Change |
|------|--------|
| `frontend/src/components/ConsultationModal.jsx` | keydown useEffect + 2 DOM id anchors + 2 Ctrl hint badge spans |
| `frontend/src/components/lab/LabResultsPanel.jsx` | `openTrigger` prop + 1 useEffect |
| `frontend/src/components/radiology/RadiologyResultsPanel.jsx` | `openTrigger` prop + 1 useEffect |
| `frontend/src/pages/hospital/IpdDetails.jsx` | 2 useState + 1 keydown useEffect + 2 prop additions |

No new files. No new API calls. No backend changes. No new components.
