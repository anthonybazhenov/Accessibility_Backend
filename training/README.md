# PDF accessibility fine-tuning

This folder is used to build training data for **OpenAI vision fine-tuning** so the app can generate better, consistent alt text for PDF images.

You need:
- **Non-compliant PDFs** (documents without good alt text)
- **Correct alt text** for each image in those PDFs (from your compliant documents or written manually)

---

## Step 1: Folder structure

From the **project root** (where `pom.xml` is), create:

```
training/
  non_compliant/    ← put your non-compliant PDFs here
  labels.json       ← you create this in Step 3
```

Create the folder:

```bash
mkdir -p training/non_compliant
```

---

## Step 2: Add your PDFs

Copy your **non-compliant** PDFs into `training/non_compliant/`:

```bash
cp /path/to/your/non-compliant/*.pdf training/non_compliant/
```

You don’t need to put compliant PDFs in a folder. You only need the **ideal alt text** for each image (see Step 3).

---

## Step 3: Generate a labels template and fill it in

Generate a template that lists every image in your PDFs:

```bash
mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningDataBuilder" -Dexec.args="--template-only"
```

This creates **`training/labels_template.json`**. It looks like:

```json
[
  {
    "filename": "report.pdf",
    "page": 1,
    "imageIndex": 0,
    "alt": "",
    "longdesc": "",
    "decorative": false
  },
  ...
]
```

- **filename** – PDF name in `non_compliant/`
- **page** – 1-based page number
- **imageIndex** – 0-based image index on that page
- **alt** – short alt text (1 sentence)
- **longdesc** – longer description for charts/diagrams, or `""`
- **decorative** – `true` if the image is decorative only

Fill in **alt** (and **longdesc** when useful) using your **compliant** documents or your own descriptions. Then save this file as:

**`training/labels.json`**

(You can copy `labels_template.json` to `labels.json` and edit that.)

---

## Step 4: Build the training file (JSONL)

Run the builder **without** `--template-only`:

```bash
mvn exec:java -Dexec.mainClass="com.husky.spring_portfolio.mvc.chatLLM.FineTuningDataBuilder"
```

This creates **`training/openai_training.jsonl`**: one line per image, in the format OpenAI expects for vision fine-tuning.

---

## Step 5: Upload and start fine-tuning (OpenAI)

You need an **OpenAI account** and **API key** with access to fine-tuning.

### 5a. Install OpenAI CLI (one-time)

```bash
pip install openai
```

Or use the OpenAI dashboard instead of the CLI.

### 5b. Upload the training file

```bash
cd training
openai api files create -f openai_training.jsonl -p "fine-tune"
```

Or in Python:

```python
from openai import OpenAI
client = OpenAI()
f = client.files.create(file=open("openai_training.jsonl", "rb"), purpose="fine-tune")
print(f.id)  # e.g. file-abc123
```

Note the **file ID** (e.g. `file-abc123`).

### 5c. Create a fine-tuning job

**Vision** fine-tuning uses a vision-capable base model, e.g. `gpt-4o-2024-08-06`:

```bash
openai api fine_tuning jobs create -t file-abc123 -m gpt-4o-2024-08-06
```

Replace `file-abc123` with your file ID from 5b.

Or in Python:

```python
job = client.fine_tuning.jobs.create(
  training_file="file-abc123",
  model="gpt-4o-2024-08-06"
)
print(job.id)
```

Note the **job ID**. Check status:

```bash
openai api fine_tuning jobs get -i <job-id>
```

Or in the dashboard: [Fine-tuning jobs](https://platform.openai.com/fine_tuning).

### 5d. Get the model name when the job finishes

When the job status is **succeeded**, the **fine-tuned model** name looks like:

`ft:gpt-4o-2024-08-06:your-org:pdf-alttext:1`

Copy that full name.

---

## Step 6: Use the fine-tuned model in the app

Set the app to use your new model.

**Option A – Environment variable**

```bash
export OPENAI_MODEL=ft:gpt-4o-2024-08-06:your-org:pdf-alttext:1
./mvnw spring-boot:run
```

**Option B – `application.properties`**

In `src/main/resources/application.properties` add or change:

```properties
openai.model=ft:gpt-4o-2024-08-06:your-org:pdf-alttext:1
```

Then run the app and upload a PDF; it will use the fine-tuned model for alt text.

---

## Summary

| Step | What you do |
|------|------------------|
| 1 | `mkdir -p training/non_compliant` |
| 2 | Put non-compliant PDFs in `training/non_compliant/` |
| 3 | Run builder with `--template-only` → edit `labels_template.json` with correct alt text → save as `training/labels.json` |
| 4 | Run builder again (no args) → creates `training/openai_training.jsonl` |
| 5 | Upload JSONL to OpenAI, create fine-tuning job with `gpt-4o-2024-08-06`, wait until done, copy model name |
| 6 | Set `OPENAI_MODEL` or `openai.model` to the new model name and run the app |

---

## Tips

- **How many examples?** OpenAI suggests on the order of hundreds of high-quality examples. Start with 50–100; add more if needed.
- **Getting labels from compliant PDFs:** If your compliant docs are tagged PDFs with `/Alt` on figures, you can copy those alt strings into `labels.json` (same order as in `labels_template.json`). The builder does not read compliant PDFs automatically; you provide labels via `labels.json`.
- **Images with people/faces:** OpenAI may skip images containing people/faces for safety. Use documents where images are charts, diagrams, or non-person content if you hit issues.
- **Cost:** Fine-tuning is billed per token. See [OpenAI pricing](https://openai.com/pricing). You can use `"detail": "low"` in image content to reduce cost (we don’t set it by default).
