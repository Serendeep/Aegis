#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

VERSION=$(grep '"version"' manifest.json | head -1 | sed 's/.*: *"\(.*\)".*/\1/')
OUTPUT="flick-aegis-otp-${VERSION}.xpi"

# Remove old build
rm -f "$OUTPUT"

# Package as .xpi (a zip with .xpi extension)
zip -r "$OUTPUT" \
    manifest.json \
    background.js \
    content.js \
    crypto.js \
    lib/ \
    icons/ \
    popup/ \
    options/ \
    -x "*.DS_Store"

echo "Built: $OUTPUT"
echo ""
echo "To install permanently in Firefox:"
echo "  1. Open Firefox and go to about:config"
echo "  2. Set xpinstall.signatures.required to false"
echo "     (only works in Firefox Developer Edition or Nightly)"
echo "  3. Go to about:addons → gear icon → Install Add-on From File"
echo "  4. Select $OUTPUT"
echo ""
echo "For regular Firefox, sign via AMO:"
echo "  npx web-ext sign --api-key=\$AMO_JWT_ISSUER --api-secret=\$AMO_JWT_SECRET"
