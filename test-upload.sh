#!/bin/bash

# Accept filename as argument, default to test-document.pdf
PDF_FILE="${1:-test-document.pdf}"

echo "🚀 Testing PDF Upload to /inputDocuments"
echo "=========================================="
echo "📁 Using file: $PDF_FILE"

# Check if Spring Boot is running on port 8085
echo "Checking if Spring Boot is running on port 8085..."
HEALTH_CHECK=$(curl -s http://localhost:8085/inputDocuments 2>&1)

if [[ $HEALTH_CHECK == *"405"* ]]; then
    echo "✅ Spring Boot is running on port 8085"
else
    echo "❌ Spring Boot is not running!"
    echo "Start it with: mvn spring-boot:run"
    exit 1
fi

# Check if file exists
if [ ! -f "$PDF_FILE" ]; then
    echo "⚠️  File not found: $PDF_FILE"
    echo "Creating a test PDF..."
    
    python3 << EOF
from reportlab.pdfgen import canvas
c = canvas.Canvas("$PDF_FILE")
c.setFont("Helvetica-Bold", 18)
c.drawString(100, 700, "City of Maplewood Parks & Recreation")
c.setFont("Helvetica", 12)
c.drawString(100, 650, "Annual Report - Fiscal Year 2025")
c.drawString(100, 600, "Test document for accessibility processing")
c.save()
print("✅ Test PDF created")
EOF
    
    if [ $? -ne 0 ]; then
        echo "❌ Could not create PDF. Please provide an existing PDF file."
        exit 1
    fi
fi

echo "📤 Uploading $PDF_FILE to /inputDocuments..."

# Upload the PDF
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8085/inputDocuments \
  -F "file=@$PDF_FILE")

# Extract HTTP status code
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
# Extract response body
BODY=$(echo "$RESPONSE" | sed '$d')

echo ""
echo "📊 Response:"
echo "HTTP Status: $HTTP_CODE"
echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"

if [ "$HTTP_CODE" = "201" ]; then
    echo ""
    echo "✅ SUCCESS! Document uploaded and processed"
    
    # Extract document ID
    DOC_ID=$(echo "$BODY" | python3 -c "import sys, json; print(json.load(sys.stdin).get('documentId', ''))" 2>/dev/null)
    
    if [ ! -z "$DOC_ID" ]; then
        echo ""
        echo "📥 Fetching processed document..."
        
        # Create output filename based on input
        OUTPUT_FILE="${PDF_FILE%.pdf}-accessible.html"
        
        # Get the processed document with HTML
        curl -s "http://localhost:8085/alteredDocuments/${DOC_ID}?includeHtml=true" | \
            python3 -c "import sys, json; d=json.load(sys.stdin); print(d.get('alteredContent', 'No HTML'))" > "$OUTPUT_FILE"
        
        echo "✅ Accessible HTML saved to: $OUTPUT_FILE"
        echo ""
        echo "🔍 Quick validation:"
        
        if grep -q '<html lang="en">' "$OUTPUT_FILE"; then
            echo "  ✅ Has lang attribute"
        else
            echo "  ❌ Missing lang attribute"
        fi
        
        if grep -q '<h[1-6]' "$OUTPUT_FILE"; then
            echo "  ✅ Has semantic headings"
        else
            echo "  ❌ No semantic headings"
        fi
        
        if grep -q '<title>Document</title>' "$OUTPUT_FILE"; then
            echo "  ❌ Still has generic title"
        elif grep -q '<title>' "$OUTPUT_FILE"; then
            TITLE=$(grep -o '<title>[^<]*</title>' "$OUTPUT_FILE" | sed 's/<title>//;s/<\/title>//')
            echo "  ✅ Has descriptive title: $TITLE"
        fi
        
        # Check for alt text on images
        if grep -q '<img' "$OUTPUT_FILE"; then
            if grep -q 'alt=' "$OUTPUT_FILE"; then
                echo "  ✅ Images have alt attributes"
            else
                echo "  ❌ Images missing alt attributes"
            fi
        fi
        
        echo ""
        echo "🌐 Open in browser: open $OUTPUT_FILE"
        echo "📄 Original PDF: $PDF_FILE"
        echo "🔄 Compare them to see accessibility improvements!"
    fi
else
    echo ""
    echo "❌ FAILED! HTTP $HTTP_CODE"
fi