# Patient Registration UX — Design Spec

**Date:** 2026-06-30  
**Scope:** Frontend only — `PatientModal.jsx`

---

## Goal

Transform patient registration from a dense 8-field form into a focused, celebratory flow that answers "What do I do next?" the moment registration completes.

---

## 1. Form Structure

### Zone A — Required fields (always visible)

Two rows, four fields:

| Row | Left | Right |
|-----|------|-------|
| 1 | Full Name * | Phone Number * |
| 2 | Age * | Gender * (radio buttons: Male / Female / Other) |

Gender changes from a `<select>` dropdown to inline radio buttons. All four fields are required and validated on submit (existing rules unchanged).

Phone field retains the existing duplicate-detection behaviour (debounced 500ms, amber warning banner).

### Zone B — Optional fields (collapsed by default)

A "＋ Add more details" toggle link below Zone A. When expanded, shows:
- Email Address
- Address (textarea)
- Medical History / Allergies (textarea)

Defaults to **collapsed** on new registration. Defaults to **expanded** on edit (since existing data lives here).

### Removed field

`Insurance` is removed from the form. It was already stripped before saving (`const { insurance, ...savePayload } = formData`) and was never persisted to the database.

### Button label

- New patient: **"Register Patient"** (was "Save Patient")
- Edit: **"Update Patient"** (unchanged)

---

## 2. Success State

After a successful new-patient POST, instead of closing the modal and showing a toast, the form is replaced by a celebration card inside the same modal:

```
🎉 Patient Registered Successfully

        [Patient Name]
      UHID: [patient.uhid || patient.id]

  [ 📅 Book Appointment → ]    ← primary, dark button
  [ Register Another Patient ] ← secondary, outline button
        Close                  ← text link
```

**"Book Appointment →"**
- Calls `onBookAppointment(registeredPatient)` if the prop is provided (dashboard uses this to open the appointment modal pre-filled for this patient)
- Then calls `onSuccess()` and `onClose()`

**"Register Another Patient"**
- Resets all form state (`formData`, `errors`, `showSuccess`, `showMoreDetails`, `duplicatePatient`) back to initial values
- Stays inside the modal, Zone A visible and focused

**"Close"**
- Calls `onSuccess()` and `onClose()`

Toast notification is **removed** for new registrations (success card replaces it). Toast is **kept** for edits.

---

## 3. Edit Mode Behaviour

Edit mode is unchanged from today:
- Zone B defaults to expanded
- No success card shown
- On successful update: `success('Patient updated successfully')` toast + `onSuccess()` + `onClose()`

---

## 4. New State Variables

| Variable | Type | Default | Purpose |
|----------|------|---------|---------|
| `showSuccess` | boolean | `false` | Controls form vs success card |
| `registeredPatient` | object\|null | `null` | Holds `{ name, id }` from POST response |
| `showMoreDetails` | boolean | `false` / `true` (edit) | Controls Zone B visibility |

All three reset when `isOpen` changes (inside existing `useEffect([isOpen, initialData])`).

---

## 5. New Prop

`onBookAppointment(patient)` — optional callback. If provided, "Book Appointment →" invokes it with `registeredPatient` before closing. The ReceptionistDashboard passes a function that opens the appointment modal pre-filled with this patient.

---

## 6. Architecture Constraints

- **One file changed**: `PatientModal.jsx` only
- **Zero backend changes**: existing `POST /hospital/patients` already returns the saved patient object with `id`
- **No new components**: success card is inline JSX inside `PatientModal`
- **Duplicate detection unchanged**: existing `useEffect` on `formData.phone` retained as-is
- **Validation unchanged**: existing `validateForm` call and rules retained

---

## 7. Files

| Action | File |
|--------|------|
| Modify | `frontend/src/components/PatientModal.jsx` |
