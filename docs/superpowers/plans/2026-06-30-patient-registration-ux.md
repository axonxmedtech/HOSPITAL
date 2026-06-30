# Patient Registration UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform `PatientModal.jsx` from a dense 8-field form into a focused 4-field registration flow with progressive disclosure and a success celebration card after registration.

**Architecture:** Single file change — `frontend/src/components/PatientModal.jsx`. Two new state variables (`showSuccess`, `registeredPatient`, `showMoreDetails`) control form vs success view and Zone B visibility. No backend changes. No new files.

**Tech Stack:** React 18, Tailwind CSS, existing `hospitalService.addPatient` / `updatePatient`

---

## File Map

| Action | File |
|--------|------|
| Modify | `frontend/src/components/PatientModal.jsx` |

---

### Task 1: Progressive Disclosure Form

**Files:**
- Modify: `frontend/src/components/PatientModal.jsx`

**Context:**
The current form has 8 fields always visible. We want only 4 required fields (Name, Phone, Age, Gender) visible by default. Gender becomes radio buttons. Email, Address, Medical History collapse under a toggle. Insurance field is removed (it was never sent to the backend — already stripped in `handleSubmit`).

- [ ] **Step 1: Read the current file**

Open `frontend/src/components/PatientModal.jsx` and confirm the current structure matches what's documented here before making changes.

- [ ] **Step 2: Add `showMoreDetails` state and reset**

Find the existing state declarations block (lines 9–13). Add one new line:

```jsx
const [showMoreDetails, setShowMoreDetails] = useState(false);
```

Then find the `useEffect` that runs on `[isOpen, initialData]` (starts around line 16). Inside the `if (isOpen)` block, add:

```jsx
setShowMoreDetails(!!initialData); // expand on edit, collapse on new
```

The full updated `useEffect` should look like:

```jsx
useEffect(() => {
    if (isOpen) {
        if (initialData) {
            setFormData({ insurance: 'NO', ...initialData });
        } else {
            setFormData({ insurance: 'NO' });
        }
        setErrors({});
        setIsSubmitting(false);
        setDuplicatePatient(null);
        setShowMoreDetails(!!initialData);
    }
}, [isOpen, initialData]);
```

- [ ] **Step 3: Replace the form fields**

Find the `<form onSubmit={handleSubmit} ...>` tag (line 128). Keep the duplicate detection banner (lines 129–145) untouched. Replace everything from `{/* Row: Name + Phone */}` down to the closing `</form>` with the following:

```jsx
                    {/* Zone A: Required fields */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <CharCountInput
                            label="Full Name"
                            required
                            value={formData.name || ''}
                            onChange={(e) => handleChange('name', e.target.value)}
                            maxLength={50}
                            placeholder="Enter patient's full name"
                            error={errors.name}
                        />
                        <CharCountInput
                            label="Phone Number"
                            required
                            type="tel"
                            value={formData.phone || ''}
                            onChange={(e) => handleChange('phone', e.target.value)}
                            maxLength={15}
                            placeholder="Enter phone number"
                            error={errors.phone}
                        />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Age <span className="text-red-600">*</span>
                            </label>
                            <input
                                type="number"
                                min="0"
                                max="120"
                                value={formData.age || ''}
                                onChange={(e) => handleChange('age', e.target.value)}
                                className={`input-field ${errors.age ? 'border-error-300 focus:ring-error-500' : ''}`}
                                placeholder="Age"
                            />
                            {errors.age && <p className="text-red-600 text-sm mt-1">{errors.age}</p>}
                        </div>
                        <div>
                            <label className="block text-sm font-semibold text-neutral-700 mb-2">
                                Gender <span className="text-red-600">*</span>
                            </label>
                            <div className="flex items-center gap-6 h-11">
                                {['MALE', 'FEMALE', 'OTHER'].map(g => (
                                    <label key={g} className="flex items-center gap-2 cursor-pointer">
                                        <input
                                            type="radio"
                                            name="gender"
                                            value={g}
                                            checked={formData.gender === g}
                                            onChange={() => handleChange('gender', g)}
                                            className="w-4 h-4 accent-blue-600"
                                        />
                                        <span className="text-sm text-neutral-700">
                                            {g.charAt(0) + g.slice(1).toLowerCase()}
                                        </span>
                                    </label>
                                ))}
                            </div>
                            {errors.gender && <p className="text-red-600 text-sm mt-1">{errors.gender}</p>}
                        </div>
                    </div>

                    {/* Zone B: Optional details (collapsible) */}
                    <div>
                        <button
                            type="button"
                            onClick={() => setShowMoreDetails(prev => !prev)}
                            className="flex items-center gap-1.5 text-sm text-blue-600 hover:text-blue-800 font-medium transition-colors"
                        >
                            <svg className={`w-4 h-4 transition-transform ${showMoreDetails ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                            </svg>
                            {showMoreDetails ? 'Hide details' : '+ Add more details'}
                        </button>

                        {showMoreDetails && (
                            <div className="mt-4 space-y-4">
                                <CharCountInput
                                    label="Email Address"
                                    type="email"
                                    value={formData.email || ''}
                                    onChange={(e) => handleChange('email', e.target.value)}
                                    maxLength={50}
                                    placeholder="Enter email address"
                                    error={errors.email}
                                />
                                <CharCountInput
                                    label="Address"
                                    textarea
                                    rows={3}
                                    value={formData.address || ''}
                                    onChange={(e) => handleChange('address', e.target.value)}
                                    maxLength={500}
                                    placeholder="Enter complete address"
                                />
                                <CharCountInput
                                    label="Medical History / Allergies"
                                    textarea
                                    rows={3}
                                    value={formData.medicalHistory || ''}
                                    onChange={(e) => handleChange('medicalHistory', e.target.value)}
                                    maxLength={500}
                                    placeholder="Any medical conditions, allergies, or important notes..."
                                />
                            </div>
                        )}
                    </div>

                    {/* Action Buttons */}
                    <div className="flex gap-4 pt-4">
                        <Button
                            type="button"
                            variant="secondary"
                            onClick={onClose}
                            className="flex-1"
                            disabled={isSubmitting}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="submit"
                            variant="primary"
                            className="flex-1"
                            loading={isSubmitting}
                        >
                            {isEdit ? 'Update Patient' : 'Register Patient'}
                        </Button>
                    </div>
```

- [ ] **Step 4: Build and verify**

```bash
cd frontend && npm run build
```

Expected: `✓ built in X.XXs` with no errors. The insurance field is gone, gender is radio buttons, "Add more details" toggle is present.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/PatientModal.jsx
git commit -m "feat(registration): progressive disclosure form — 4 required fields, gender radio buttons, collapsible details"
```

---

### Task 2: Success State After Registration

**Files:**
- Modify: `frontend/src/components/PatientModal.jsx`

**Context:**
After a successful new patient POST, instead of closing the modal with a toast, show a celebration card with the patient's name, UHID, and two actions: "Book Appointment →" and "Register Another Patient". Edit mode keeps existing behavior.

- [ ] **Step 1: Add `showSuccess` and `registeredPatient` state**

Find the state declarations block. Add two new lines after `showMoreDetails`:

```jsx
const [showSuccess, setShowSuccess] = useState(false);
const [registeredPatient, setRegisteredPatient] = useState(null);
```

- [ ] **Step 2: Reset new state on modal open**

Inside the `useEffect([isOpen, initialData])` block, add:

```jsx
setShowSuccess(false);
setRegisteredPatient(null);
```

The full updated useEffect:

```jsx
useEffect(() => {
    if (isOpen) {
        if (initialData) {
            setFormData({ insurance: 'NO', ...initialData });
        } else {
            setFormData({ insurance: 'NO' });
        }
        setErrors({});
        setIsSubmitting(false);
        setDuplicatePatient(null);
        setShowMoreDetails(!!initialData);
        setShowSuccess(false);
        setRegisteredPatient(null);
    }
}, [isOpen, initialData]);
```

- [ ] **Step 3: Add `onBookAppointment` prop**

The component signature is currently:

```jsx
const PatientModal = ({ isOpen, onClose, onSuccess, initialData }) => {
```

Change it to:

```jsx
const PatientModal = ({ isOpen, onClose, onSuccess, initialData, onBookAppointment }) => {
```

- [ ] **Step 4: Capture the registered patient in `handleSubmit`**

Find the `handleSubmit` function. The new-patient branch currently reads:

```jsx
const result = await hospitalService.addPatient(savePayload);
success('Patient added successfully');
console.log('[PatientModal] Patient added, calling onSuccess');
```

Replace those three lines with:

```jsx
const result = await hospitalService.addPatient(savePayload);
setRegisteredPatient(result);
setShowSuccess(true);
setIsSubmitting(false);
return; // don't call onSuccess/onClose yet
```

**Important:** The `return` exits `handleSubmit` before the existing `onSuccess(); onClose();` calls that follow the try/catch, so the modal stays open showing the success card. The `finally` block sets `isSubmitting(false)` — since we already set it and returned, add `return` before `finally` runs. Safest approach: restructure the try block so the new patient path returns early:

The full updated `handleSubmit`:

```jsx
const handleSubmit = async (e) => {
    e.preventDefault();
    setErrors({});
    setIsSubmitting(true);

    const rules = {
        name: ['required', 'name'],
        age: ['required', 'age'],
        gender: ['required'],
        phone: ['required', 'phone'],
        email: ['email']
    };

    const validationErrors = validateForm(formData, rules);
    if (Object.keys(validationErrors).length > 0) {
        setErrors(validationErrors);
        setIsSubmitting(false);
        return;
    }

    try {
        const { insurance, ...savePayload } = formData;

        if (isEdit) {
            await hospitalService.updatePatient(formData.id, savePayload);
            success('Patient updated successfully');
            onSuccess();
            onClose();
        } else {
            const result = await hospitalService.addPatient(savePayload);
            setRegisteredPatient(result);
            setShowSuccess(true);
        }
    } catch (err) {
        console.error("Failed to save patient", err);
        const msg = err.response?.data?.message || 'Operation failed';
        toastError(msg);
    } finally {
        setIsSubmitting(false);
    }
};
```

- [ ] **Step 5: Add success card JSX**

Find `if (!isOpen) return null;` (line 100). Immediately after it, before the main `return (`, add the success card conditional. The full return block becomes:

```jsx
if (!isOpen) return null;

if (showSuccess && registeredPatient) {
    return (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-organic w-full max-w-md animate-scale-in overflow-hidden">
                {/* Header */}
                <div className="bg-white px-8 py-6 border-b border-gray-200 flex justify-between items-center">
                    <h3 className="text-xl font-bold text-neutral-800">Registration Complete</h3>
                    <button
                        onClick={() => { onSuccess(); onClose(); }}
                        className="w-10 h-10 rounded-xl hover:bg-gray-100 flex items-center justify-center text-neutral-400 hover:text-neutral-600 transition-all"
                    >
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                {/* Success card body */}
                <div className="px-8 py-8 flex flex-col items-center text-center gap-6">
                    <div>
                        <p className="text-4xl mb-3">🎉</p>
                        <p className="text-lg font-bold text-neutral-800">
                            {registeredPatient.name} has been registered
                        </p>
                        <p className="text-sm text-neutral-500 mt-1">
                            UHID: <span className="font-semibold text-neutral-700">
                                {registeredPatient.uhid || registeredPatient.id}
                            </span>
                        </p>
                    </div>

                    <div className="w-full space-y-3">
                        <Button
                            variant="primary"
                            className="w-full"
                            onClick={() => {
                                if (onBookAppointment) onBookAppointment(registeredPatient);
                                onSuccess();
                                onClose();
                            }}
                        >
                            📅 Book Appointment →
                        </Button>
                        <Button
                            variant="secondary"
                            className="w-full"
                            onClick={() => {
                                setFormData({ insurance: 'NO' });
                                setErrors({});
                                setShowSuccess(false);
                                setRegisteredPatient(null);
                                setDuplicatePatient(null);
                                setShowMoreDetails(false);
                            }}
                        >
                            Register Another Patient
                        </Button>
                        <button
                            type="button"
                            onClick={() => { onSuccess(); onClose(); }}
                            className="text-sm text-neutral-500 hover:text-neutral-700 underline transition-colors"
                        >
                            Close
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}

return (
    // ... existing modal JSX unchanged ...
```

- [ ] **Step 6: Build and verify**

```bash
cd frontend && npm run build
```

Expected: `✓ built in X.XXs` with no errors.

Manual check:
1. Open receptionist dashboard → Register Patient
2. Fill Name, Phone, Age, Gender → click "Register Patient"
3. Modal should show success card with patient name + UHID
4. "Register Another Patient" should reset and show empty form
5. "Book Appointment →" should close modal
6. Edit a patient → modal shows all fields expanded, submitting closes with toast (no success card)

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/PatientModal.jsx
git commit -m "feat(registration): success celebration card with Book Appointment CTA after patient registration"
```

---

## Self-Review

**Spec coverage:**
- ✅ Zone A (4 required fields) — Task 1
- ✅ Gender radio buttons — Task 1
- ✅ Insurance field removed — Task 1 (not sent to backend; removed from form)
- ✅ Zone B collapsible (email, address, medical history) — Task 1
- ✅ "Register Patient" button label — Task 1
- ✅ Success card (🎉 name + UHID) — Task 2
- ✅ "Book Appointment →" with `onBookAppointment` prop — Task 2
- ✅ "Register Another Patient" resets form — Task 2
- ✅ "Close" calls `onSuccess` + `onClose` — Task 2
- ✅ Edit mode unchanged (expanded Zone B, no success card, toast kept) — Task 1 + Task 2
- ✅ Duplicate detection banner untouched — not modified in either task
- ✅ `showMoreDetails` resets on modal open — Task 1
- ✅ `showSuccess` / `registeredPatient` reset on modal open — Task 2
- ✅ `patient.uhid || patient.id` for UHID display — Task 2

**Placeholder scan:** None found.

**Type consistency:** `registeredPatient` is set from `hospitalService.addPatient` response (the raw patient object). Used as `registeredPatient.name`, `registeredPatient.uhid`, `registeredPatient.id` — all fields present on the entity.
