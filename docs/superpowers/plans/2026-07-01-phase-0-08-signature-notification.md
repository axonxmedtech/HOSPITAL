# Phase 0.8 — Signature & Notification Scaffolds Implementation Plan

**Goal:** Scaffold the core notification and signature services needed by all subsequent consent, surgical OT, and MRD auditing phases.

---

## Target Files & Packages
- `backend/src/main/java/com/hms/entity/SignatureSlot.java` [NEW]
- `backend/src/main/java/com/hms/entity/DocumentVersion.java` [NEW]
- `backend/src/main/java/com/hms/repository/SignatureSlotRepository.java` [NEW]
- `backend/src/main/java/com/hms/repository/DocumentVersionRepository.java` [NEW]
- `backend/src/main/java/com/hms/service/hospital/SignatureAndDocumentService.java` [NEW]
- `backend/src/main/java/com/hms/service/whatsapp/WhatsAppService.java` (modify to add public wrapper)
- `backend/src/main/java/com/hms/service/hospital/NotificationService.java` [NEW]
- `backend/src/main/java/com/hms/config/DatabaseMigrationRunner.java`
- `setup/schema-full.sql`

---

## Tasks

### Task 1: Extend WhatsAppService & Scaffold NotificationService

- [ ] **Step 1: Update `WhatsAppService.java`**
  Add a public wrapper method to allow the facade to route generic templates:
  ```java
  public void sendWhatsApp(Long hospitalId, Long patientId, String phone,
                           String templateName, String msgType,
                           List<String> templateParams, String mediaUrl) {
      doSend(hospitalId, patientId, phone, templateName, msgType, templateParams, mediaUrl);
  }
  ```

- [ ] **Step 2: Create `NotificationService.java`**
  Implement the notification facade:
  - `sendWebSocketRefresh(Long hospitalId, String eventType, Long entityId)`
  - `sendWhatsAppNotification(Long hospitalId, Long patientId, String phone, String templateName, String msgType, List<String> params, String mediaUrl)`

---

### Task 2: Create Signature and Document Version Entities & Repositories

- [ ] **Step 1: Create `SignatureSlot.java`**
  - Fields: `id`, `hospitalId`, `signerRole` (String), `signerName` (String), `signerRelationship` (String), `signedAt`, `documentType` (String), `documentId` (String), `signatureImageBase64` (Text), `signatureHash` (String).

- [ ] **Step 2: Create `DocumentVersion.java`**
  - Fields: `id`, `hospitalId`, `documentType` (String), `documentId` (String), `version` (Integer), `contentUrl` (String), `updatedBy` (Long), `updatedAt`.

- [ ] **Step 3: Create `SignatureSlotRepository.java` & `DocumentVersionRepository.java`**

---

### Task 3: Implement SignatureAndDocumentService

- [ ] **Step 1: Create `SignatureAndDocumentService.java`**
  Implement methods:
  - `saveSignatureSlot(SignatureSlot slot)` (includes tenant-isolation guard validation).
  - `getSignaturesForDocument(String documentType, String documentId)` (includes tenant-isolation validation).
  - `incrementDocumentVersion(Long hospitalId, String documentType, String documentId, String contentUrl, Long userId)` (increments or inserts a new version of the document, ensuring tenant scope).

---

### Task 4: Database Migration & Schema Mirroring

- [ ] **Step 1: Update `DatabaseMigrationRunner.java`**
  Add `migrateSignatureNotificationSchema()` to create tables `signature_slots` and `document_versions`. Register in `runMigrations()`.

- [ ] **Step 2: Update `setup/schema-full.sql`**
  Add `CREATE TABLE` statements for the two new tables.

---

### Task 5: Integration & Verification Tests

- [ ] **Step 1: Write `SignatureAndNotificationTest.java`**
  - Verify signature slot persistence, retrieval, and tenant isolation.
  - Verify document version increments correctly.
  - Verify notification facade broadcasts to web socket and dispatches to WhatsApp (using Mockito).

---

## Verification Plan

### Automated Tests
- Run `mvn -q test` in `backend/` to verify all tests pass.
- Run `npm run build` in `frontend/` to verify Vite compile check.
