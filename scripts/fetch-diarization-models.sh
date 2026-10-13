#!/usr/bin/env bash
# Fetch the local speaker-diarization models that :diarize expects, and verify every byte against a
# pinned SHA-256. Everything runs on-device; no cloud service is involved.
#
# Usage: scripts/fetch-diarization-models.sh [DEST_DIR]
#   DEST_DIR defaults to ./build/diarization-models
#
# Resulting layout (matches DiarizationModels.Pyannote3Dspeaker):
#   DEST_DIR/
#     sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx
#     3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx
set -euo pipefail

DEST="${1:-build/diarization-models}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "${DEST}" = /* ]] || DEST="${ROOT}/${DEST}"

SEG_VERSION="speaker-segmentation-models"
SEG_BUNDLE="sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
SEG_DIR="sherpa-onnx-pyannote-segmentation-3-0"
SEG_FILE="model.int8.onnx"
SEG_BUNDLE_SHA256="24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488"
SEG_FILE_SHA256="d582f4b4c6b48205de7e0643c57df0df5615a3c176189be3fc461e9d18827b5d"

EMB_VERSION="speaker-recongition-models"
EMB_FILE="3dspeaker_speech_campplus_sv_zh-cn_16k-common.onnx"
EMB_SHA256="f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11"

GH="https://github.com/k2-fsa/sherpa-onnx/releases/download"
SEG_URL="${GH}/${SEG_VERSION}/${SEG_BUNDLE}"
EMB_URL="${GH}/${EMB_VERSION}/${EMB_FILE}"

mkdir -p "${DEST}/tmp"

verify() { # <sha256> <file>
    echo "${1}  ${2}" | sha256sum -c - >/dev/null
}

# Download <url> to <file> only when <file> is absent or fails its pinned <sha256>, and always
# re-verify the result. A file that exists but does NOT match is deleted before re-downloading:
# resuming a mismatched file with `curl -C -` would keep the corrupt prefix and never self-heal.
fetch_verified() { # <sha256> <url> <file>
    if [[ -f "${3}" ]] && verify "${1}" "${3}"; then
        return 0
    fi
    rm -f "${3}"
    echo "Downloading ${2}"
    curl -fL --retry 3 -o "${3}" "${2}"
    verify "${1}" "${3}"
}

echo "Fetching diarization models into ${DEST}"

# --- Segmentation (pyannote/segmentation-3.0, MIT) ------------------------------------------
bundle="${DEST}/tmp/${SEG_BUNDLE}"
fetch_verified "${SEG_BUNDLE_SHA256}" "${SEG_URL}" "${bundle}"

tar xjf "${bundle}" -C "${DEST}" "${SEG_DIR}/${SEG_FILE}"
verify "${SEG_FILE_SHA256}" "${DEST}/${SEG_DIR}/${SEG_FILE}"

# --- Speaker embedding (3D-Speaker CAM++, Apache-2.0) ---------------------------------------
embedding="${DEST}/${EMB_FILE}"
fetch_verified "${EMB_SHA256}" "${EMB_URL}" "${embedding}"

rm -rf "${DEST}/tmp"
echo "OK: diarization models verified under ${DEST}"
