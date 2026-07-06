# PlatformDashboard.jsx Refactoring Summary

## Overview
Refactored PlatformDashboard.jsx to support a grouped sidebar tabs structure while preserving all existing business logic, modals, and handlers.

## Changes Made

### 1. Core Helper Functions (Lines 114-145)
Added three new helper functions to manage the new grouped tab structure:

- **`parseTab(tab)`** - Parses tab strings in format `"group:subtab"` or top-level tabs like `"dashboard"`
  - Returns: `{ isTopLevel: true, tab }` for top-level or `{ isTopLevel: false, group, subtab }` for grouped
  
- **`getHospitalTypeFromGroup(group)`** - Converts group name to hospital type
  - `"hospital"` → `"HOSPITAL"`
  - `"clinic"` → `"CLINIC"`
  - `"pharmacy"` → `"PHARMACY"`
  
- **`getCurrentHospitalType()`** - Gets the current active hospital type based on activeTab
  - Returns `null` for top-level tabs, hospital type string for grouped tabs

### 2. Updated `getEntityType()` Function (Lines 133-138)
Modified to use the new `parseTab()` helper while maintaining backward compatibility with existing code.

### 3. Tabs Array Restructured (Lines 624-665)
Converted from flat structure to grouped structure:

**Old Structure:**
```javascript
const tabs = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'hospitals', label: 'Hospitals' },
  { id: 'clinics', label: 'Clinics' },
  // ... etc
];
```

**New Structure:**
```javascript
const tabs = [
  { id: 'dashboard', label: 'Dashboard', isTopLevel: true },
  {
    id: 'hospital',
    label: 'Hospital',
    group: true,
    subItems: [
      { id: 'hospital:hospitals', label: 'Hospitals' },
      { id: 'hospital:medicines', label: 'Medicines' },
      // ... etc
    ]
  },
  // ... clinic and pharmacy groups similarly
];
```

### 4. Navbar Title Logic (Lines 667-680)
Added `getTabLabel()` function to dynamically extract tab labels from the grouped structure. Works for both top-level tabs (dashboard, audit_logs) and grouped subtabs (hospital:hospitals, clinic:medicines, etc.).

### 5. PageHeader Subtitle Logic (Lines 709-761)
Refactored to parse tab format and determine context-appropriate subtitles for grouped tabs. Replaced direct activeTab comparisons with subtab extraction from parsed activeTab.

### 6. API Call Props (Lines 906-924)
Updated component rendering to pass `hospitalType` prop:
- `<PlansTab hospitalType={hospitalType} />`
- `<PlatformMedicinesTab hospitalType={hospitalType} />`
- `<PlatformInventoryItemsTab hospitalType={hospitalType} />`

The `hospitalType` is extracted dynamically based on current group using `getCurrentHospitalType()`.

### 7. Data Loading useEffect (Lines 258-276)
Updated to parse activeTab and extract subtab name for conditional loading logic. Now handles both:
- Top-level tabs: `dashboard`, `audit_logs`
- Grouped subtabs: `hospital:hospitals`, `clinic:medicines`, `pharmacy:plans`, etc.

### 8. Content Rendering Logic (Lines 926-987)
Refactored large conditional render block to:
1. Extract subtab from activeTab format
2. Route to correct table/component based on subtab name
3. Pass appropriate entity type to HospitalsTable component

### 9. Create Hospital Modal (Lines 1031-1196)
Updated modal to:
- Extract entity type from activeTab subtab
- Use computed `entityName` and `entityNameCap` variables throughout
- Replaced hard-coded activeTab comparisons with derived variables
- Properly closes modal function with `()}`

### 10. Component Props (Updated Files)
Modified three components to accept optional `hospitalType` prop:

**PlatformMedicinesTab.jsx (Line 19)**
```javascript
export default function PlatformMedicinesTab({ hospitalType = null }) {
```

**PlatformInventoryItemsTab.jsx (Line 17)**
```javascript
export default function PlatformInventoryItemsTab({ hospitalType = null }) {
```

**PlansTab.jsx (Line 37)**
```javascript
export default function PlansTab({ hospitalType = null }) {
  // ... initialize typeFilter with hospitalType
  const [typeFilter, setTypeFilter] = useState(hospitalType || '');
```

## Preserved Functionality

✓ All modal states and handlers remain unchanged
✓ All business logic (create, edit, delete, toggle status) works identically
✓ API calls use same platformService methods
✓ Confirmation dialogs and error handling preserved
✓ Pagination and sorting logic unaffected
✓ WebSocket and real-time updates unchanged
✓ Form validation and error messages intact
✓ Profile and password reset modals work the same

## Tab Structure Examples

### Top-level Tabs (No Group)
- URL param: `?tab=dashboard`
- URL param: `?tab=audit_logs`

### Grouped Tabs
- URL param: `?tab=hospital:hospitals` → Hospital group, Hospitals subtab
- URL param: `?tab=hospital:medicines` → Hospital group, Medicines subtab
- URL param: `?tab=clinic:clinics` → Clinic group, Clinics subtab
- URL param: `?tab=pharmacy:pharmacies` → Pharmacy group, Pharmacies subtab

## Sidebar Component Integration

The Sidebar component (unchanged) already supported grouped tabs with `subItems`:
- Top-level items render directly
- Items with `subItems` property render as expandable groups
- Clicking group shows/hides subtabs using built-in expansion logic
- subItems click directly navigates to tab with full ID (e.g., "hospital:hospitals")

## Testing Checklist

- [x] TypeScript compilation passes
- [x] Vite build succeeds without errors
- [x] All imports resolve correctly
- [x] Helper functions correctly parse tab formats
- [x] Tabs array matches new grouped structure
- [x] Components accept hospitalType prop
- [x] No breaking changes to existing modals or handlers
