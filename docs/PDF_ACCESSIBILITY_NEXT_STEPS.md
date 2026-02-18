# PDF Accessibility – Next Steps to Get It Working

## 1. Set your OpenAI API key

The app reads the key from the environment or from `application.properties`.

**Option A – Environment variable (recommended)**  
Before starting the app:

```bash
export OPENAI_API_KEY=sk-your-key-here
```

**Option B – application.properties**  
Add (and do not commit this file with a real key):

```properties
openai.api.key=sk-your-key-here
```

Without a key, the pipeline still runs but uses placeholder alt text (no real AI descriptions).

---

## 2. Run the app and create the upload directory

```bash
# From project root
./mvnw spring-boot:run
```

The app uses `volumes/uploads` for stored PDFs. That directory is created automatically when you upload a file. To create it beforehand:

```bash
mkdir -p volumes/uploads
```

---

## 3. Test the pipeline

**Upload a PDF**

```bash
curl -X POST http://localhost:8085/inputDocuments \
  -F "file=@/path/to/your/document.pdf"
```

You should get a JSON response with `documentId`, `filename`, and `status`.

**List altered documents**

```bash
curl http://localhost:8085/alteredDocuments
```

**Get accessibility report for a document**

```bash
curl http://localhost:8085/alteredDocuments/{documentId}/report
```

**Download the accessible HTML**

```bash
curl -o accessible.html "http://localhost:8085/alteredDocuments/{documentId}/download?format=html"
```

---

## 4. Optional: use a different model

Default is `gpt-4o`. To use another model (e.g. a fine-tuned model):

```bash
export OPENAI_MODEL=ft:gpt-4o:your-org:pdf-alttext:1
```

Or in `application.properties`:

```properties
openai.model=ft:gpt-4o:your-org:pdf-alttext:1
```

---

## 5. Optional: allow larger PDFs

If uploads fail for bigger files, increase the limit in `WebAppInitializer.java` (e.g. change `MAX_UPLOAD_SIZE` from 5MB to a larger value).

---

## 6. Summary of what’s implemented

| Step | Status |
|------|--------|
| Upload PDF to `/inputDocuments` | Done |
| Extract text + images with context | Done |
| Generate alt text via OpenAI Vision (gpt-4o) | Done |
| WCAG-style accessibility report | Done |
| Accessible HTML output | Done |
| `GET /alteredDocuments/{id}/report` | Done |
| `GET /alteredDocuments/{id}/download` | Done |
| Fine-tuned model support (via config) | Ready – set `openai.model` |
| **Fine-tuning from your dataset** | See **`training/README.md`** for step-by-step setup |
| Writing tags into PDF (remediated PDF file) | Not implemented – only HTML is produced |

---

## 7. If something fails

- **Upload returns 500** – Check logs; ensure `volumes/uploads` is writable and disk isn’t full.
- **Alt text is generic** – Confirm `OPENAI_API_KEY` is set and the key is valid.
- **CORS errors from a frontend** – The controller allows `localhost:3000`, `4000`, `5500`; add your frontend origin if needed.
- **Database errors** – SQLite DB is at `volumes/sqlite.db`; ensure the `volumes` directory exists and is writable.
