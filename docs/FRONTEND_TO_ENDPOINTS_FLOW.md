# Document flow: Frontend → Endpoints → Back to frontend

Use this to draw a diagram of how documents move from the frontend through each endpoint and what methods run.

**Base URL (dev):** `http://localhost:8085`  
**Controller:** `chatDocApiController`  
**Service:** `chatDocService`  
**Repository:** `chatDocRepository` (JPA, entity `chatDoc`)

---

## 1. Upload document (frontend → backend → response)

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `POST /inputDocuments` with `Content-Type: multipart/form-data`, form field **`file`** (PDF file). |
| **Endpoint** | Controller | `uploadDocument(@RequestParam("file") MultipartFile file)` |
| **Controller does** | chatDocApiController | 1) Validates `file` not empty and `contentType == "application/pdf"`<br>2) Calls `documentService.processPdfDocument(file)`<br>3) On success: builds JSON with message, documentId, filename, pipelineStatus, outcomeStatus, status<br>4) Returns `201 CREATED` with that JSON, or 400/500 with error body |
| **Service method** | chatDocService | **processPdfDocument(file)** — see “Upload pipeline” below. |
| **Back to frontend** | Response | JSON: `{ message, documentId, filename, pipelineStatus, outcomeStatus, status }` |

---

## 2. Upload pipeline (inside processPdfDocument)

What happens **inside** `chatDocService.processPdfDocument(file)` from start to finish. Each phase updates **pipelineStatus** and persists when noted.

| Order | Phase | pipelineStatus | Methods used | Persist? |
|-------|--------|----------------|--------------|----------|
| 1 | Save file & create record | **UPLOADED** | `Files.createDirectories`, `Files.write` (original PDF to disk), `documentRepository.save(document)` | Yes |
| 2 | Extract text | **EXTRACTED** | `extractTextFromPdf(pdfBytes)` → text<br>`extractImagesFromPdf(pdfBytes, originalContent)` → list of ImageInfo | Yes (originalContent) |
| 3 | Alt text for images | **ALT_DONE** | `generateAltTextForImages(images)` → list of AltTextResult; set altTextJson<br>**→ Calls fine-tuning network:** OpenAI API (vision), model = `openai.model` (base or fine-tuned `ft:gpt-4o:...`) | Yes (if images) |
| 4 | Accessibility report | **REPORT_DONE** | `generateAccessibilityReport(document, images)` → AccessibilityReport; set accessibilityReportJson, complianceLabel, labelSource (if null) | Yes |
| 5 | Build HTML | **HTML_DONE** | `generateRemediationPlanWithModel(...)` → plan or null<br>**→ (When implemented) fine-tuning network:** LLM/fine-tuned model to produce RemediationPlan JSON<br>If plan: `createAccessibleHtmlFromPlan(...)` else `createAccessibleHtml(...)`<br>Set alteredContent, remediationPlanJson (if plan), outcomeStatus, status | Yes |
| — | On exception | **FAILED** | Set pipelineStatus, outcomeStatus, status to FAILED; save; rethrow | Yes |

**Outcome (outcomeStatus):**  
- `report.getErrors() > 0` → **NEEDS_REVIEW**  
- else `report.getWarnings() > 0` → **REMEDIATED_WITH_WARNINGS**  
- else → **REMEDIATED**

---

## 2b. Fine-tuning network (in the upload pipeline)

During **POST /inputDocuments** → **processPdfDocument**, the backend can call an **external fine-tuning / model network**. Show these as separate nodes in your diagram when drawing the pipeline.

| Where in pipeline | What calls the network | Network / API | Config | Purpose |
|-------------------|------------------------|---------------|--------|---------|
| **ALT_DONE** (phase 3) | `generateAltTextForImages(images)` | **OpenAI API** — `POST https://api.openai.com/v1/chat/completions` (vision) | `openai.api.key`, `openai.model` (e.g. `gpt-4o` or fine-tuned `ft:gpt-4o-2024-08-06:org:pdf-alttext:1`) | For each image: send image + context text; get back JSON with alt, longdesc, decorative, confidence. |
| **HTML_DONE** (phase 5) | `generateRemediationPlanWithModel(document, report, images)` | **(Planned)** LLM / fine-tuned model that returns RemediationPlan JSON | TBD when implemented | Produce structured plan (issues, actions) from report + content; used by `createAccessibleHtmlFromPlan`. |

**Flow for diagram:**  
- **Frontend** → **Backend** → (optional) **Fine-tuning network (OpenAI / custom model)** → **Backend** → **DB** → **Backend** → **Frontend**.  
- Alt-text: Backend sends image(s) + prompt to OpenAI; receives AltTextResult-style JSON; backend stores `altTextJson` and uses it when building HTML.  
- Remediation plan: When wired, backend would send report + content to model; receive RemediationPlan JSON; store `remediationPlanJson` and pass plan to `createAccessibleHtmlFromPlan`.

**Training the models (offline, not in request flow):**  
- Alt-text: Use `FineTuningDataBuilder` + `training/openai_training.jsonl` to fine-tune a vision model; set `openai.model` to the new model id.  
- Plan: Use `FineTuningPlanDataBuilder` + human-labeled docs + `training/openai_plan_training.jsonl` to fine-tune a plan model; when implemented, point `generateRemediationPlanWithModel` at that model.

---

## 3. List altered documents

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `GET /alteredDocuments` (no body). |
| **Endpoint** | Controller | `getAlteredDocuments()` |
| **Controller does** | chatDocApiController | Calls `documentService.getAlteredDocuments()`, returns list as `200 OK` or 500. |
| **Service method** | chatDocService | **getAlteredDocuments()** — filters repository by outcomeStatus (REMEDIATED / REMEDIATED_WITH_WARNINGS / NEEDS_REVIEW) or pipelineStatus HTML_DONE or legacy status; filters by non-empty alteredContent. |
| **Repository** | chatDocRepository | `findAll()` (then filtered in service). |
| **Back to frontend** | Response | JSON array of **chatDoc** objects (id, originalFilename, originalContent, alteredContent, paths, JSON fields, pipelineStatus, outcomeStatus, status, complianceLabel, labelSource, timestamp, etc.). |

---

## 4. Get one document by ID

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `GET /alteredDocuments/{id}`. Optional query: `?includeHtml=true` to include alteredContent. |
| **Endpoint** | Controller | `getDocumentById(@PathVariable Long id, @RequestParam boolean includeHtml)` |
| **Controller does** | chatDocApiController | Calls `documentService.getDocumentById(id)`. If null → 404. Else builds a map of all stored fields (id, originalFilename, paths, originalContent, altTextJson, accessibilityReportJson, pipelineStatus, outcomeStatus, status, timestamp, complianceLabel, labelSource, auditJson, remediationPlanJson). If `includeHtml==true` adds alteredContent; else adds `alteredContentIncluded: false`. |
| **Service method** | chatDocService | **getDocumentById(id)** → `documentRepository.findById(id).orElse(null)`. |
| **Back to frontend** | Response | JSON object with the fields above, or 404. |

---

## 5. Get accessibility report for a document

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `GET /alteredDocuments/{id}/report`. |
| **Endpoint** | Controller | `getAccessibilityReport(@PathVariable Long id)` |
| **Controller does** | chatDocApiController | Calls `documentService.getAccessibilityReport(id)`. If null → 404. Else returns report as JSON. |
| **Service method** | chatDocService | **getAccessibilityReport(documentId)** — loads document by id, parses `accessibilityReportJson` into an **AccessibilityReport** object, returns it. |
| **Back to frontend** | Response | JSON **AccessibilityReport** (documentId, filename, total_issues, errors, warnings, info, issues[], isTagged, hasReadingOrder, imagesWithAltText, totalImages) or 404. |

---

## 6. Download remediated document (HTML or PDF)

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `GET /alteredDocuments/{id}/download?format=html` (or `format=pdf`). |
| **Endpoint** | Controller | `downloadDocument(@PathVariable Long id, @RequestParam String format)` |
| **Controller does** | chatDocApiController | Calls `documentService.getDocumentById(id)`. If null → 404. If format is pdf and alteredPdfPath set: returns file from disk (FileSystemResource). Else: writes document.getAlteredContent() to a temp file and returns it as HTML with `Content-Disposition: attachment`. |
| **Service method** | chatDocService | **getDocumentById(id)** only. |
| **Back to frontend** | Response | File download (HTML or PDF) with attachment filename, or 404. |

---

## 7. Set human compliance label

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `PATCH /alteredDocuments/{id}/label?value=COMPLIANT` (or NONCOMPLIANT or UNKNOWN). |
| **Endpoint** | Controller | `setHumanLabel(@PathVariable Long id, @RequestParam("value") String value)` |
| **Controller does** | chatDocApiController | Validates value is COMPLIANT, NONCOMPLIANT, or UNKNOWN (else 400). Calls `documentService.updateHumanLabel(id, value)`. If null → 404. Else returns summary JSON. |
| **Service method** | chatDocService | **updateHumanLabel(id, complianceLabel)** — loads document by id, sets complianceLabel and labelSource = "HUMAN", saves, returns updated chatDoc. |
| **Repository** | chatDocRepository | `findById(id)`, `save(document)`. |
| **Back to frontend** | Response | JSON: `{ id, filename, complianceLabel, labelSource, pipelineStatus, outcomeStatus, status }` or 400/404. |

---

## 8. Get remediation plan schema (no document)

| Step | Who | What |
|------|-----|------|
| **Frontend sends** | Client | `GET /remediation-plan/schema`. |
| **Endpoint** | Controller | `getRemediationPlanSchema()` |
| **Controller does** | chatDocApiController | Returns `RemediationPlanSchema.getJsonSchema()` as JSON. |
| **Back to frontend** | Response | JSON Schema object for RemediationPlan (for validation/parsing). |

---

## Summary table for diagram

| Endpoint | Method | Controller method | Service method(s) | Returns to frontend |
|----------|--------|-------------------|-------------------|----------------------|
| `/inputDocuments` | POST | uploadDocument | processPdfDocument | documentId, filename, pipelineStatus, outcomeStatus, status |
| `/alteredDocuments` | GET | getAlteredDocuments | getAlteredDocuments → findAll + filter | List&lt;chatDoc&gt; |
| `/alteredDocuments/{id}` | GET | getDocumentById | getDocumentById | Map of doc fields (optional alteredContent) |
| `/alteredDocuments/{id}/report` | GET | getAccessibilityReport | getAccessibilityReport, getDocumentById | AccessibilityReport |
| `/alteredDocuments/{id}/download` | GET | downloadDocument | getDocumentById | File (HTML or PDF) |
| `/alteredDocuments/{id}/label` | PATCH | setHumanLabel | updateHumanLabel, getDocumentById | Summary (id, filename, complianceLabel, labelSource, status…) |
| `/remediation-plan/schema` | GET | getRemediationPlanSchema | RemediationPlanSchema.getJsonSchema() | JSON Schema |

---

## Data flow overview (for diagram)

```
[Frontend]
    │
    │ POST /inputDocuments (file)
    ▼
[chatDocApiController.uploadDocument]
    │
    │ processPdfDocument(file)
    ▼
[chatDocService.processPdfDocument]
    │  UPLOADED → save
    │  EXTRACTED → extractTextFromPdf, extractImagesFromPdf → save
    │  ALT_DONE → generateAltTextForImages ──────────────┐
    │              │                                     │
    │              ▼                                     ▼
    │         [Fine-tuning network: OpenAI API (vision)]   │
    │              model = openai.model (base or ft:...)  │
    │              → AltTextResult JSON per image         │
    │              │                                     │
    │              ◄─────────────────────────────────────┘
    │              → save (altTextJson)
    │  REPORT_DONE → generateAccessibilityReport → save (complianceLabel, labelSource)
    │  HTML_DONE   → generateRemediationPlanWithModel ─── (when implemented: call plan model)
    │              → createAccessibleHtml(FromPlan) or createAccessibleHtml
    │              → save (alteredContent, remediationPlanJson, outcomeStatus)
    ▼
[chatDocRepository.save]  →  DB (chat_doc)
    │
    │ return chatDoc
    ▼
[Controller]  →  JSON { documentId, filename, pipelineStatus, outcomeStatus, status }
    │
    ▼
[Frontend]

Later:
  GET /alteredDocuments        → getAlteredDocuments() → List<chatDoc>
  GET /alteredDocuments/{id}   → getDocumentById(id)   → doc fields (+ optional HTML)
  GET .../report               → getAccessibilityReport(id) → AccessibilityReport
  GET .../download             → getDocumentById(id)  → file stream (HTML/PDF)
  PATCH .../label?value=...    → updateHumanLabel(id, value) → summary JSON
```

---

## Final step: how the frontend gets the accessible HTML

After upload, the backend has stored the accessible HTML in **chatDoc.alteredContent**. The frontend can get it in any of these ways:

| Option | Endpoint | What you get |
|--------|----------|----------------|
| **1. From the list** | `GET /alteredDocuments` | Full **chatDoc** for each completed document. Each object includes **alteredContent** (the HTML). So the frontend can list all altered docs and use `doc.alteredContent` to render or save. (Payload can be large if many docs.) |
| **2. One doc with HTML** | `GET /alteredDocuments/{id}?includeHtml=true` | Single document with all fields **and** **alteredContent**. Use when you already have the document id (e.g. from the upload response) and want that doc’s HTML. |
| **3. Download as file** | `GET /alteredDocuments/{id}/download?format=html` | The HTML as a **file download** (attachment). Use when the user should “download” the accessible version; the response is the raw HTML file, not JSON. |

So **yes** — one natural final step is: frontend calls **GET /alteredDocuments**, gets the list, and uses each item’s **alteredContent** as the accessible HTML. Alternatively the frontend can call **GET /alteredDocuments/{id}?includeHtml=true** for a single doc (e.g. using the **documentId** from the upload response) or **GET .../download?format=html** to get the HTML as a downloadable file.

You can turn each box into a diagram node and each arrow into a labeled edge (e.g. “POST file”, “processPdfDocument”, “save”, “JSON response”). Include **Fine-tuning network (OpenAI)** as a separate node between the service and the DB for the ALT_DONE phase; optionally add a **Plan model** node for HTML_DONE when that is implemented.
