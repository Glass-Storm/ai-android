#!/usr/bin/env bash
# Fetch the vendored sherpa-onnx Android AAR that :asr and :tts resolve from libs/ (flatDir).
# The artifact is a binary and is intentionally not committed; this script pins its SHA-256.
set -euo pipefail

VERSION="1.13.8"
SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${ROOT}/libs"
ARTIFACT="${DEST}/sherpa-onnx-${VERSION}.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-${VERSION}.aar"

mkdir -p "${DEST}"

if [[ -f "${ARTIFACT}" ]] && echo "${SHA256}  ${ARTIFACT}" | sha256sum -c - >/dev/null 2>&1; then
    echo "sherpa-onnx ${VERSION} already present and verified: ${ARTIFACT}"
    exit 0
fi

echo "Downloading ${URL}"
curl -fL --retry 3 -C - -o "${ARTIFACT}" "${URL}"

echo "${SHA256}  ${ARTIFACT}" | sha256sum -c -
echo "OK: ${ARTIFACT}"
