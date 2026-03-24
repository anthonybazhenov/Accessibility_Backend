# PDF accessibility fine-tuning

This folder is used to build training data for **two** fine-tuning flows:

- **A) Alt-text fine-tune** – vision model for better image alt text (from PDFs + labels file).
- **B) Remediation-plan fine-tune** – model for structured remediation plans (from DB: human-labeled docs + stored plans).

---

## Required environment and config

### Environment variables

| Variable | Purpose |
|----------|---------|
| `OPENAI_API_KEY` | API key for alt-text generation at runtime and for fine-tuning (upload jobs, etc.). **Required** for AI alt text. |
| `OPENAI_MODEL` | Model name at runtime (e.g. `gpt-4o` or a fine-tuned model like `ft:gpt-4o-2024-08-06:org:slug:1`). |
| `TRAINING_DB_URL` | (Optional) JDBC URL for plan exporter. Default: `jdbc:sqlite:volumes/sqlite.db` from project root. |

Set before running the app or exporters, e.g.:

```bash
export OPENAI_API_KEY=sk-your-key-here
export OPENAI_MODEL=gpt-4o
```

### application.properties (examples, no real keys)

In `src/main/resources/application.properties` you can set:

```properties
# OpenAI (optional here if you use env vars)
# openai.api.key=sk-your-key-here
# openai.model=gpt-4o
# For a fine-tuned model after training:
# openai.model=ft:gpt-4o-2024-08-06:your-org:pdf-alttext:1
```

The app already reads `openai.api.key` and `openai.model` with defaults from env (`OPENAI_API_KEY`, `OPENAI_MODEL`). Omit real keys from the repo; use env or a local override.

---

## Where output artifacts are stored (chatDoc)

After processing a PDF, the app stores these in the **chatDoc** entity (DB and API):

| Field | Description |
|-------|-------------|
| `originalFilename` | Uploaded PDF name. |
| `originalContent` | Extracted text from PDF. |
| `alteredContent` | Generated accessible HTML. |
| `originalPdfPath` | Path to stored original PDF (e.g. `volumes/uploads/...`). |
| `altTextJson` | JSON array of alt-text results per image. |
| `accessibilityReportJson` | WCAG-style accessibility report. |
| `remediationPlanJson` | Remediation plan (when the model or a human provides one). |
| `complianceLabel` | `COMPLIANT` / `NONCOMPLIANT` / `UNKNOWN`. |
| `labelSource` | `HEURISTIC` (from report) or `HUMAN` (set via PATCH). |
| `auditJson` | Optional reference audit. |
| `status` / `pipelineStatus` | Processing state. |

Plan training export uses: `original_content`, `accessibility_report_json`, `alt_text_json`, `audit_json`, `remediation_plan_json`, and only includes docs with `label_source = HUMAN` and a non-empty `remediation_plan_json`.

---

## How to label documents (PATCH) for plan training

To mark a document as human-labeled so it can be used for **remediation-plan** training:

```bash
# Set compliance label (COMPLIANT, NONCOMPLIANT, or UNKNOWN). Sets labelSource=HUMAN.
curl -X PATCH "http://localhost:8085/alteredDocuments/{id}/label?value=COMPLIANT"
```

Replace `{id}` with the document ID (from `POST /inputDocuments` or `GET /alteredDocuments`). After this, the doc has `labelSource=HUMAN` and `complianceLabel` set. Ensure the doc also has `remediation_plan_json` (from the model or from a human-curated plan) so the plan exporter can include it.

---

# A) Alt-text fine-tune

Use this to fine-tune a **vision** model for better, consistent alt text for PDF images.

You need: **non-compliant PDFs** and **correct alt text** for each image (from compliant docs or written manually).

## A1. Folder structure

From the **project root**:

```bash
mkdir -p training/non_compliant
```

Put non-compliant PDFs in `training/non_compliant/`. You will create `training/labels.json` in A3.

## A2. Generate labels template and fill it in

```bash
mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningDataBuilder" -Dexec.args="--template-only"
```

This creates **`training/labels_template.json`** (one entry per image: filename, page, imageIndex, alt, longdesc, decorative). Fill in **alt** and **longdesc**, then save as **`training/labels.json`**.

## A3. Export JSONL

```bash
mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningDataBuilder"
```

**Output:** `training/openai_training.jsonl` (one line per image; user = prompt + image, assistant = alt JSON).

## A4. Upload and fine-tune (OpenAI)

Upload the file and create a **vision** fine-tuning job (e.g. base model `gpt-4o-2024-08-06`). See [OpenAI fine-tuning](https://platform.openai.com/docs/guides/vision-fine-tuning). Note the resulting model name (e.g. `ft:gpt-4o-2024-08-06:org:pdf-alttext:1`).

## A5. Use the fine-tuned model

Set `OPENAI_MODEL` (or `openai.model` in `application.properties`) to the new model name. The app will use it for alt-text generation.

---

# B) Remediation-plan fine-tune

Use this to fine-tune a model to output **structured remediation plans** (issues + actions) from document content and the accessibility report.

You need: documents in the DB with **human labels** and a stored **remediation plan** (`remediation_plan_json`).

## B1. Prerequisites

- App has been run; DB has documents (from `POST /inputDocuments`).
- For each doc you want in the training set:
  - Label it via **PATCH** (see [How to label documents](#how-to-label-documents-patch-for-plan-training)) so `labelSource=HUMAN` and `complianceLabel` is set.
  - Ensure `remediation_plan_json` is set (from the model when implemented, or by hand via DB/API).

## B2. Export JSONL for plan fine-tuning

From the **project root** (DB: `volumes/sqlite.db` unless `TRAINING_DB_URL` is set):

```bash
mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningPlanDataBuilder"
```

**Output:** `training/openai_plan_training.jsonl` – one line per doc: **user** = input payload (originalContent truncated, reportSummary, imagesSummary, optional auditJson), **assistant** = stored `remediation_plan_json`.

## B3. Template-only: emit a plan template for one doc

To get a fill-in template for document id `1`:

```bash
mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningPlanDataBuilder" -Dexec.args="--template-only 1"
```

**Output:** `training/plan_template_1.json` with `inputPayload` (same shape as runtime) and an empty `remediationPlan` (target, compliance, issues, actions). Fill it in, then store as `remediation_plan_json` for that doc and re-run the exporter, or build JSONL manually.

## B4. Upload and fine-tune (OpenAI)

Use `openai_plan_training.jsonl` with a **text** fine-tuning job (no vision). After the job succeeds, you can plug the new model into your plan-generation step when implemented.

---

## Summary

| Flow | Exporter command | Output artifact |
|------|------------------|-----------------|
| **A) Alt-text** | `mvn exec:java -Dexec.mainClass="...FineTuningDataBuilder"` (with or without `-Dexec.args="--template-only"`) | `training/openai_training.jsonl`, `training/labels.json`, `training/labels_template.json` |
| **B) Plan** | `mvn exec:java -Dexec.mainClass="...FineTuningPlanDataBuilder"` or `-Dexec.args="--template-only <id>"` | `training/openai_plan_training.jsonl`, `training/plan_template_<id>.json` |

- **Label docs for plan training:** `PATCH /alteredDocuments/{id}/label?value=COMPLIANT|NONCOMPLIANT|UNKNOWN`
- **Config:** `OPENAI_API_KEY`, `OPENAI_MODEL`; optional `openai.api.key` / `openai.model` in `application.properties` (see examples above).

---

## Tips

- **Alt-text:** Aim for 50–100+ high-quality image examples; get labels from compliant PDFs or manual review.
- **Plan:** Ensure each training doc has both a human label (PATCH) and a filled `remediation_plan_json`.
- **Cost:** Fine-tuning is billed per token; see [OpenAI pricing](https://openai.com/pricing).
- **Vision safety:** OpenAI may skip images with people/faces; use charts/diagrams where possible for alt-text training.
