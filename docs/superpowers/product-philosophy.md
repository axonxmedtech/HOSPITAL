# Strynkix Product Engineering — Design Philosophy

Source: internal "Strynkix Product Engineering" chapters 1–5. This is a reference
for how UX/architecture decisions in this codebase should be made — consult it
when writing specs (`docs/superpowers/specs/`) or plans (`docs/superpowers/plans/`).

## Golden Rule

Never build a feature. Always solve a problem. Use the Five Whys to find the
root cause before designing a screen (e.g. "we need another report" often
means "billing data is fragmented across departments", not "add a report").

## Mental models to borrow from real life, not invent

When designing a screen, ask: "what real-world thing already works like this?"
Then copy that interaction pattern instead of inventing a new one.

- **Inbox** — every role's home screen is "today's work" (tasks), not a module landing page.
- **Timeline** — patient history reads like a feed (Facebook/Instagram-style), not a table.
- **Cards over tables** — cards tell a story (patient + status + alerts); tables just show data.
- **Bed/ward maps** — green/yellow/red occupancy grids instead of list views.
- **Checklists** — OT prep, discharge, admission flows as checkable steps, not generic forms.
- **Traffic lights** — green/yellow/red only; don't invent new status colors.
- **GPS/navigation** — guide the user to "Next: Give Medicine" rather than presenting a module picker.
- **Shopping cart** — billing accrues automatically from services rendered, not manual entry.
- **One workspace per patient** — everything about a patient (labs, billing, notes, meds) lives together; never lose patient context across screens.
- **Progress bars** — "70% of today's tasks done" — completion motivates.

## Cognitive-load rules (apply when reviewing any new screen)

- **Hick's Law** — fewer choices, faster decisions. Show ~4 primary actions, hide the rest behind progressive disclosure.
- **Fitts's Law** — primary actions should be big and easy to reach.
- **Miller's Rule** — group related fields/sections into ~5–9 chunks, not flat 20+ field forms.
- **Recognition over recall** — search/autocomplete with visual cues beats "remember the ID".
- **Peak-End Rule** — the last step of a flow (e.g. discharge/billing) shapes the overall impression; optimize endings.
- **Zeigarnik Effect** — show remaining/incomplete work counts to drive completion.
- **Immediate feedback** — every action gets a visible confirmation (✓ Saved, ✓ Medicine Given).
- **Goal-gradient** — multi-step flows (admission, discharge) show step progress.
- **Error prevention over error messages** — suggest correct values before submit (e.g. dose ranges) rather than rejecting after.
- **Information scent** — labels should make the destination obvious before clicking.
- **Muscle memory** — keep button positions and behavior consistent across releases.
- **Calm technology** — notify only when action is required, not continuously.

## Architectural direction: tasks over modules

Long-term direction (not a mandate for every PR): model hospital work as a
generic task/event stream — register, consult, test, medicate, bill — rather
than siloed modules. New features should ask "is this a task with an owner,
priority, due time, and dependencies?" before becoming a one-off module screen.
Existing module-specific task lists (nurse tasks, doctor rounds, OT checklist)
are reasonable steps toward this if kept structurally similar (same shape:
owner, priority, due, status, dependencies) so they can converge later.

## Chapter 6: Universal Patient Workspace (One Patient. One Screen. Everything.)

* "Patients don't belong to modules. Modules belong to patients."
* Keep all patient information in one workspace context instead of dividing it across modules.
* **Sticky Header**: Patient basics (name, UHID, room, attending doctor, status) must remain visible during scroll to prevent losing context.
* **The Overview Tab**: Must answer "What do I need to know immediately?" (allergies, blood group, current diagnosis, active meds, pending tests, vitals, bills, follow-up).
* **Workspace Tabs**: Keep all data under one screen — Overview, Timeline (chronological history feed), Clinical (notes, vitals, allergies), Medicines (active/stopped/missed), Investigations (test status/results), Procedures, Billing, Insurance, Documents (consents, letters, files), and Communication (SMS, WhatsApp, emails).
* **Floating Action Button (FAB)**: A single visible `+` button to start any action (New Order, Note, Prescription, Investigation, Procedure) from inside the patient workspace.
* **Search within Context**: Allow searching specifically within a patient's context (e.g. searching "Paracetamol" directly displays its history for this patient).

## Chapter 7: Universal Hospital Search (Ctrl+K / Cmd+K)

* **Always Accessible**: The search bar should be always visible (top center on desktop, top on mobile).
* **Natural Language Queries**: Search should support descriptive typing (e.g., "Rahul pneumonia admission" or "Yesterday CT") rather than strict codes.
* **Relevance Ranking**: Prioritize results by context: Current Patient -> Frequently Used -> Recent -> Everything Else.
* **Typo Tolerance & Guidance**: Forgive spelling mistakes. Never show a dead-end "No Results" screen; always recommend next steps (e.g., "No patient found. [Create New Patient]").
* **Quick Actions**: Enable direct commands from search (e.g., searching "Rahul" allows quick actions like "Call", "Admit", "Print", "Prescription", "Timeline").
* **Fast Response**: Search results must render in under 300 milliseconds.

## Chapter 8: Hospital Command Center (Digital Twin)

* **The 30-Second Rule**: An administrator must be able to understand the entire state and health of the hospital in 30 seconds.
* **Hospital Health Score**: A single aggregated metric calculating waiting time, critical alerts, medicine delays, bed occupancy, OT delays, and staff load.
* **Live Patient Flow**: Visual pipeline mapping patients across OPD Waiting -> Consultation -> Lab -> Pharmacy -> Billing -> Completed.
* **Live Bed Map**: High-level grid layout of wards (Green = Available, Red = Occupied, Yellow = Cleaning).
* **Delay Radar**: Actionable indicators for overdue tasks (medicine overdue, discharges blocked, lab delays).
* **Predictive Alerts**: Alert operations before bottlenecks occur (e.g., predicting radiology delays based on queue capacity).

## Strynkix Vision: Core Principles

* **Everything Starts With the Patient**: Revolve all workflows and care teams around the patient context (One Care Team collaboration) rather than isolated modular silos.
* **Automated Information Flow**: Information must flow automatically between domains (e.g. Doctor prescribes -> Nurse receives task -> Billing updates charge -> Pharmacy prepares stock). Avoid phone calls or manual coordination.
* **The Hospital Has Memory**: Never ask a patient to repeat information; retrieve and verify existing data from the system's memory.
* **Every Delay Has An Owner**: Any delay (medicine overdue, blocked discharge, OT delay) must be immediately visible and assigned to a clear owner for accountability.
* **Software Coordinates, Humans Decide**: Software organizes tasks and flags clinical/operational outcomes; humans retain absolute clinical and operational oversight.
* **The Five Layers of Strynkix**: 
  1. *Experience* (beautiful UI)
  2. *Workflow* (movement of work)
  3. *Task Engine* (task routing/tracking)
  4. *Intelligence* (predictions, recommendations)
  5. *Knowledge* (institutional memory and outcomes)

## Chapter 11: Domain-Driven Design (DDD)

* **Reality-First Mapping**: Mirror the real-world domains of the hospital directly in software entities (clinical, nursing, lab, billing, pharmacy, OT, etc.).
* **Loose Coupling & Events**: Domains must never directly modify each other's databases or internal tables. All cross-domain operations must occur asynchronously via Domain Events.
* **Bounded Contexts**: Respect domain-specific definitions (e.g., "Order" means a diagnostic test in the Clinical domain, but a medicine requisition in the Pharmacy domain).
* **Aggregate Roots**: Identify the central entity of each domain (e.g., `Patient Visit` in Reception, `Consultation` in Clinical, `Invoice` in Billing) and execute all operations through it.
* **Ubiquitous Language**: Keep terminologies (e.g., using "Patient Visit" instead of mixing "Visit" and "Encounter") strictly identical across design, QA, code, and business discussions.

## Chapter 12: Event-Driven Architecture (EDA)

* **Permanent Event Records**: Events represent immutable history of what occurred (e.g. `PATIENT_REGISTERED`, `MEDICINE_PRESCRIBED`, `REPORT_READY`). Save them permanently for auditing and replay capabilities.
* **The Event Bus Nervous System**: Use a central event bus to publish events. Subscribers (e.g. Billing, Notification, Timeline) listen and process tasks independently. This decouples the system and allows adding new modules (e.g., Insurance) without modifying existing ones.
* **Idempotency & Resilience**: Ensure all event subscribers can handle duplicate events safely (e.g., never duplicate billing charges on retry). If a subscriber service is down, events should queue and sync when active.
* **Event Priority**: Prioritize events accordingly (e.g., `PATIENT_CRITICAL` runs immediately, `MEDICINE_PRESCRIBED` within seconds, and financial logs in the background).

## Chapter 13: Universal Task Engine

* **Tasks Over CRUD**: Model work around active responsibilities and timelines (Tasks) rather than simple database entries (CRUD forms).
* **The Universal Task Model**: Every single task follows the exact same structure across the hospital: Owner (Person, Team, or Role), Priority, Due Time, Dependencies, and Checklist.
* **State & Escalation**: Tasks progress through states (`CREATED` -> `ASSIGNED` -> `IN_PROGRESS` -> `COMPLETED`/`FAILED`). Overdue tasks automatically trigger alerts and escalate up the organizational hierarchy.
* **Urgency-Based Sorting**: The inbox orders tasks by urgency and clinical priority rather than creation time.
* **Shift Handover Automation**: Open tasks automatically move to the incoming shift's inbox based on rules, requiring no manual handovers.

## Chapter 14: The Rules Engine (Build Once. Configure Forever.)

* **Decoupled Business Rules**: Keep policy rules (e.g. deposit requirements, discount limits, medicine safety checks) separate from the core business logic. Business code should query the rules engine dynamically, allowing rules to be configured without rewriting or redeploying code.
* **Rule Categories**: Apply rules across categories (Clinical, Operational, Billing, Insurance, Admission, OT, Nursing, Notification, Security).
* **If/Then Structure**: Structure rules uniformly using logical conditions and corresponding execution actions.
* **Conflict Resolution**: Resolve rule conflicts using defined priority values (e.g. emergency safety bypass rules always win over financial insurance rules).
* **Rule Versioning & Auditing**: Keep previous rule configurations active for ongoing historical cases; never overwrite rules directly. Provide rule simulations before deploying to see impact.

## Chapter 15: Workflow Engine (What Happens Next?)

* **Visible Workflows**: Represent multi-step journeys (e.g. Patient Admission, Lab Diagnostics, Surgery Prep) as structured, configurable sequences of steps instead of hardcoding transition logic.
* **Workflow States & Transitions**: Maintain absolute paths and transition validations (e.g. preventing a patient from moving directly from "Under Treatment" back to "Registered" without clinical discharge steps).
* **Parallel Work support**: Workflows must support parallel steps (e.g., executing Bed Allocation, Billing Deposits, and Nursing Assessments simultaneously).
* **Waiting States & Human Approval**: Support pausing steps for human decisions or machine events (e.g., waiting for laboratory results before doctor review).
* **Template-Driven Workflows**: Utilize customizable workflow templates for standard procedures (e.g. Caesarean Section, Normal OPD).

## Chapter 16: Permission & Security Engine (Trust Into Every Click)

* **Four Security Questions**: For every request, validate: *Who is the user? What are they trying to do? Are they allowed? Should this action be recorded?*
* **Dynamic Access (ABAC)**: Supplement Role-Based Access Control (RBAC) with Attribute-Based Access Control (ABAC) to enforce contextual security (e.g. a Doctor can only view a patient's chart if they are the Attending Doctor or on their active Care Team).
* **Break-Glass Emergency Access**: Enable emergency override to access restricted data instantly, but trigger automatic priority alerts and create a mandatory audit trail.
* **Temporary Permissions & Delegation**: Enforce automatic expiration on visiting/temporary access. Provide structured delegation controls instead of sharing passwords.

## Chapter 17: Audit & Time Machine Engine (Nothing is Lost)

* **The Golden Rule**: Data is never deleted or overwritten directly; it is marked inactive, cancelled, or superseded. Maintain all historical versions of data.
* **Git-Like Version Comparison**: Reconstruct and display exact visual differences between historical document/bill/clinical record versions.
* **Time Machine Slider**: Support reconstructing the exact state of the entire hospital (occupied beds, open tasks, outstanding bills, staff shifts) at any past timestamp.
* **Digital Signatures**: Protect clinical and financial records (e.g. prescriptions, summaries, refunds) with electronic signatures for non-repudiation.
* **Read Audits**: Audit reading access of sensitive records (like HIV reports or psychiatric notes) to verify authorization.

## Chapter 18: Communication & Notification Engine

* **Information Over People**: Automate communications to reduce manual calls and paper slips.
* **Multi-Channel & Synchronized Routing**: Coordinate alerts through the correct channels (In-app, SMS, WhatsApp, Email) and synchronize read states across all devices.
* **Urgency-Based Interruption**: Restrict loud alerts/popups to critical clinical events (e.g., Code Blue); deliver non-urgent logs silently.
* **Actionable and Bundled Alerts**: Every notification must provide a direct action button (e.g., "[Review CBC Report]"). Bundle multiple related notifications (e.g., "5 Medicines Due") to minimize distraction.
* **Automatic Escalation Paths**: Escalate unacknowledged clinical tasks up the staff hierarchy automatically (e.g., Nurse -> Charge Nurse -> Supervisor).

## Using this in practice

- When writing a new spec, state the problem (not the feature) and which
  mental model the screen borrows from.
- When reviewing a design, run the Five-Second Test: can a new user say what
  the screen is for, what to click, and what happens next?
- Once a month-ish: look for one thing to simplify, automate, or remove —
  not just add.
