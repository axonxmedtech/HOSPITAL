# Keyboard Shortcuts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Ctrl+D / Ctrl+L (ConsultationModal) and Ctrl+L / Ctrl+R (IpdDetails) keyboard shortcuts so the doctor can jump to clinical fields without lifting their hands from the keyboard.

**Architecture:** Four independent file changes. ConsultationModal gets a keydown listener (active only when `isOpen`) plus DOM id anchors and hint badges. LabResultsPanel and RadiologyResultsPanel each get a new `openTrigger` number prop that opens their order form when incremented. IpdDetails adds two counter states and a keydown listener that increments them, then passes them as props to the panels.

**Tech Stack:** React 18, existing `useState` / `useEffect`, native DOM `addEventListener`

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/components/ConsultationModal.jsx` |
| Modify | `frontend/src/components/lab/LabResultsPanel.jsx` |
| Modify | `frontend/src/components/radiology/RadiologyResultsPanel.jsx` |
| Modify | `frontend/src/pages/hospital/IpdDetails.jsx` |

---

### Task 1: ConsultationModal — Shortcuts, Anchors, Hint Badges

**Files:**
- Modify: `frontend/src/components/ConsultationModal.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/components/ConsultationModal.jsx`. Confirm:
- `isOpen` is a prop (line ~9)
- `setActiveTab` state setter exists (Clinical / Prescription tabs)
- `setFormData` state setter exists; `formData.labRequired` is a boolean field
- The Diagnosis `CharCountInput` is in the clinical tab block (around line 494)
- The lab checkbox `<div>` starts with `<div className="mt-4">` and contains `id="lab-checkbox"` (around line 865)
- At least one existing `useEffect` is already present in the file

- [ ] **Step 2: Add the keydown useEffect**

Find the block of existing `useEffect` calls in the component (after state declarations). Add the following new `useEffect` after the last existing one:

```jsx
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
        e.preventDefault();
        setActiveTab('clinical');
        setTimeout(() => {
          document.getElementById('consultation-diagnosis-anchor')
            ?.querySelector('textarea')
            ?.focus();
        }, 0);
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'l') {
        e.preventDefault();
        setActiveTab('clinical');
        setFormData(prev => ({ ...prev, labRequired: true }));
        setTimeout(() => {
          document.getElementById('consultation-lab-section')
            ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }, 0);
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);
```

`setTimeout(..., 0)` defers the DOM operation until after the `setActiveTab` state update has re-rendered.

- [ ] **Step 3: Add DOM anchor + hint badge to Diagnosis field**

Find the Diagnosis `CharCountInput` (it looks like this):
```jsx
                                    <CharCountInput
                                        label="Diagnosis"
                                        textarea
                                        rows={3}
                                        value={formData.diagnosis}
                                        onChange={(e) => handleChange('diagnosis', e.target.value)}
                                        maxLength={500}
                                        placeholder="Enter diagnosis..."
                                    />
```

Replace with:
```jsx
                                    <div id="consultation-diagnosis-anchor">
                                        <CharCountInput
                                            label="Diagnosis"
                                            textarea
                                            rows={3}
                                            value={formData.diagnosis}
                                            onChange={(e) => handleChange('diagnosis', e.target.value)}
                                            maxLength={500}
                                            placeholder="Enter diagnosis..."
                                        />
                                        <p className="text-xs text-gray-400 mt-1">
                                            Shortcut: <kbd className="font-mono bg-gray-100 border border-gray-200 rounded px-1 py-0.5 text-gray-500">Ctrl+D</kbd>
                                        </p>
                                    </div>
```

- [ ] **Step 4: Add DOM anchor + hint badge to Lab section**

Find the lab checkbox outer div (it contains `id="lab-checkbox"` further inside):
```jsx
                                        <div className="mt-4">
                                            <div className="flex items-center gap-3">
                                                <input
                                                    id="lab-checkbox"
                                                    type="checkbox"
                                                    checked={formData.labRequired}
                                                    onChange={(e) => setFormData(prev => ({ ...prev, labRequired: e.target.checked }))}
                                                    className="h-4 w-4"
                                                />
                                                <label htmlFor="lab-checkbox" className="text-sm font-medium text-gray-700">Order Lab Tests</label>
                                            </div>
```

Replace with:
```jsx
                                        <div id="consultation-lab-section" className="mt-4">
                                            <div className="flex items-center gap-3">
                                                <input
                                                    id="lab-checkbox"
                                                    type="checkbox"
                                                    checked={formData.labRequired}
                                                    onChange={(e) => setFormData(prev => ({ ...prev, labRequired: e.target.checked }))}
                                                    className="h-4 w-4"
                                                />
                                                <label htmlFor="lab-checkbox" className="text-sm font-medium text-gray-700">
                                                    Order Lab Tests
                                                    <kbd className="ml-2 font-mono bg-gray-100 border border-gray-200 rounded px-1 py-0.5 text-xs text-gray-400">Ctrl+L</kbd>
                                                </label>
                                            </div>
```

Note: only the outer `<div>` gains the `id="consultation-lab-section"` attribute and the `<label>` gains the `<kbd>` badge. Everything inside (the lab options grid) is unchanged.

- [ ] **Step 5: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 6: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/components/ConsultationModal.jsx && git commit -m "feat(shortcuts): Ctrl+D=diagnosis Ctrl+L=lab in ConsultationModal"
```

---

### Task 2: LabResultsPanel — `openTrigger` Prop

**Files:**
- Modify: `frontend/src/components/lab/LabResultsPanel.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/components/lab/LabResultsPanel.jsx`. Confirm:
- Line ~23: `export default function LabResultsPanel({ ipdAdmissionId, patientId, canOrder = false }) {`
- Line ~26: `const [showOrderForm, setShowOrderForm] = useState(false);`
- At least one `useEffect` already exists (the `fetchOrders` effect)

- [ ] **Step 2: Add `openTrigger` prop and trigger effect**

Find:
```js
export default function LabResultsPanel({ ipdAdmissionId, patientId, canOrder = false }) {
```

Replace with:
```js
export default function LabResultsPanel({ ipdAdmissionId, patientId, canOrder = false, openTrigger = 0 }) {
```

Then find the existing `useEffect` that calls `fetchOrders`:
```js
  useEffect(() => { fetchOrders(); }, [fetchOrders]);
```

Add a new `useEffect` immediately after it:
```js
  useEffect(() => {
    if (openTrigger > 0 && canOrder) setShowOrderForm(true);
  }, [openTrigger, canOrder]);
```

- [ ] **Step 3: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 4: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/components/lab/LabResultsPanel.jsx && git commit -m "feat(shortcuts): openTrigger prop on LabResultsPanel"
```

---

### Task 3: RadiologyResultsPanel — `openTrigger` Prop

**Files:**
- Modify: `frontend/src/components/radiology/RadiologyResultsPanel.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/components/radiology/RadiologyResultsPanel.jsx`. Confirm:
- The component signature line (similar to LabResultsPanel — check the exact current props)
- Line ~17: `const [showOrderForm, setShowOrderForm] = useState(false);`
- At least one existing `useEffect`

- [ ] **Step 2: Add `openTrigger` prop and trigger effect**

Find the `export default function RadiologyResultsPanel(...)` line. Add `openTrigger = 0` to the destructured props:

If current signature is:
```js
export default function RadiologyResultsPanel({ ipdAdmissionId, patientId, canOrder = false }) {
```

Replace with:
```js
export default function RadiologyResultsPanel({ ipdAdmissionId, patientId, canOrder = false, openTrigger = 0 }) {
```

Then find the existing fetch `useEffect` (the one that loads radiology orders). Add immediately after it:
```js
  useEffect(() => {
    if (openTrigger > 0 && canOrder) setShowOrderForm(true);
  }, [openTrigger, canOrder]);
```

- [ ] **Step 3: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 4: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/components/radiology/RadiologyResultsPanel.jsx && git commit -m "feat(shortcuts): openTrigger prop on RadiologyResultsPanel"
```

---

### Task 4: IpdDetails — Trigger States + Keydown Listener + Pass Props

**Files:**
- Modify: `frontend/src/pages/hospital/IpdDetails.jsx`

- [ ] **Step 1: Read the file landmarks**

Open `frontend/src/pages/hospital/IpdDetails.jsx`. Confirm:
- The state declarations block (first ~200 lines of the component)
- Line ~720: `<LabResultsPanel` — note the exact props currently passed
- Line ~728: `<RadiologyResultsPanel` — note the exact props currently passed
- At least one existing `useEffect`

- [ ] **Step 2: Add two trigger state variables**

Find the block of `useState` declarations. Add the two new states near the end of that block:

Find a suitable anchor (e.g., the last `useState` in the block — look for something like `const [saving, setSaving] = useState(false);` or similar):

Add after the last `useState` in the block:
```js
    const [labOpenTrigger, setLabOpenTrigger] = useState(0);
    const [radOpenTrigger, setRadOpenTrigger] = useState(0);
```

- [ ] **Step 3: Add keydown useEffect**

Find the last existing `useEffect` in the component. Add a new `useEffect` after it:

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

- [ ] **Step 4: Pass props to LabResultsPanel**

Find the `<LabResultsPanel` JSX (around line 720). Read the current props being passed. Add `openTrigger={labOpenTrigger}` to the existing props list.

For example, if it currently reads:
```jsx
                    <LabResultsPanel
                        ipdAdmissionId={admission?.id}
                        patientId={admission?.patient?.id}
                        canOrder={canOrder}
                    />
```

Add `openTrigger={labOpenTrigger}`:
```jsx
                    <LabResultsPanel
                        ipdAdmissionId={admission?.id}
                        patientId={admission?.patient?.id}
                        canOrder={canOrder}
                        openTrigger={labOpenTrigger}
                    />
```

IMPORTANT: Read the actual current props before editing — the prop names may differ from the example above. Only add `openTrigger={labOpenTrigger}`; do not change any existing props.

- [ ] **Step 5: Pass props to RadiologyResultsPanel**

Find the `<RadiologyResultsPanel` JSX (around line 728). Add `openTrigger={radOpenTrigger}` to the existing props list, using the same pattern as Step 4.

- [ ] **Step 6: Build to verify**

```bash
cd e:/Projects/HOSPITAL/frontend && npm run build 2>&1 | tail -5
```

Expected: `✓ built in X.XXs` — zero errors.

- [ ] **Step 7: Commit**

```bash
cd e:/Projects/HOSPITAL && git add frontend/src/pages/hospital/IpdDetails.jsx && git commit -m "feat(shortcuts): Ctrl+L=lab Ctrl+R=radiology keyboard triggers in IpdDetails"
```

---

## Self-Review

**Spec coverage:**
- ✅ Ctrl+D in ConsultationModal: `setActiveTab('clinical')` + focus via `consultation-diagnosis-anchor` — Task 1 Step 2
- ✅ Ctrl+L in ConsultationModal: `setActiveTab('clinical')` + `labRequired: true` + scroll to `consultation-lab-section` — Task 1 Step 2
- ✅ `id="consultation-diagnosis-anchor"` wrapper div on Diagnosis field — Task 1 Step 3
- ✅ `id="consultation-lab-section"` on lab outer div — Task 1 Step 4
- ✅ Ctrl+D hint badge (`<kbd>Ctrl+D</kbd>`) near Diagnosis — Task 1 Step 3
- ✅ Ctrl+L hint badge (`<kbd>Ctrl+L</kbd>`) near Order Lab Tests label — Task 1 Step 4
- ✅ `event.preventDefault()` on all intercepted shortcuts — Task 1 Step 2, Task 4 Step 3
- ✅ Listener teardown on `isOpen` false / unmount — Task 1 Step 2 (`return () => document.removeEventListener`)
- ✅ `openTrigger` prop on `LabResultsPanel` — Task 2 Step 2
- ✅ `useEffect` opens form when `openTrigger > 0 && canOrder` — Task 2 Step 2
- ✅ `openTrigger` prop on `RadiologyResultsPanel` — Task 3 Step 2
- ✅ Same `useEffect` pattern — Task 3 Step 2
- ✅ `labOpenTrigger` / `radOpenTrigger` states in IpdDetails — Task 4 Step 2
- ✅ Keydown listener in IpdDetails: Ctrl+L increments `labOpenTrigger`, Ctrl+R increments `radOpenTrigger` — Task 4 Step 3
- ✅ `openTrigger={labOpenTrigger}` passed to `<LabResultsPanel>` — Task 4 Step 4
- ✅ `openTrigger={radOpenTrigger}` passed to `<RadiologyResultsPanel>` — Task 4 Step 5
- ✅ No new API calls, no new components, no backend changes — architecture

**Placeholder scan:** None found. All steps contain exact code.

**Type consistency:**
- `openTrigger` is `number` (default `0`) in both Task 2 and Task 3 signatures.
- `labOpenTrigger` / `radOpenTrigger` are `number` states (Task 4 Step 2), passed as `openTrigger={labOpenTrigger}` / `openTrigger={radOpenTrigger}` (Task 4 Steps 4–5). Match.
- `setLabOpenTrigger(t => t + 1)` increments correctly — `openTrigger > 0` check in Tasks 2/3 is satisfied after first press. Match.
- `consultation-diagnosis-anchor` used in Task 1 Step 2 handler and defined in Task 1 Step 3. Match.
- `consultation-lab-section` used in Task 1 Step 2 handler and defined in Task 1 Step 4. Match.
