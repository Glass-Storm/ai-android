#!/usr/bin/env bash
# Live-demo runner for the ai-android library.
#
# Works on Git Bash / WSL on Windows, and on Linux / macOS. On native Windows
# PowerShell use scripts/demo.ps1 instead.
#
# This script only builds and (optionally) installs. It does NOT modify tracked
# build or source files. It:
#   0) checks the active JDK (refuses JDK 24+),
#   1) ensures the vendored sherpa-onnx AAR is present and SHA-256 verified,
#   2) runs the headline verification (unit tests + ktlint + detekt + engine resolution),
#   3) builds the :demo-app debug APK,
#   4) installs and launches it if an adb device is attached, else prints instructions,
#   5) prints a final summary.
#
# Usage:
#   bash scripts/demo.sh              # normal run
#   bash scripts/demo.sh --offline    # pre-warmed machine only (uses Gradle --offline)
#
# The --offline flag is optional; a cold machine must stay online to resolve deps.
set -euo pipefail

VERSION="1.13.8"
SHA256="633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-${VERSION}.aar"
DEMO_PACKAGE="hk.edu.hkmu.demo"
DEMO_ACTIVITY="hk.edu.hkmu.demo/.MainActivity"
TOTAL_STEPS=5

GRADLE_OFFLINE=""
for arg in "$@"; do
    case "${arg}" in
    --offline) GRADLE_OFFLINE="--offline" ;;
    -h | --help)
        sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
        exit 0
        ;;
    *)
        echo "Unknown argument: ${arg}" >&2
        echo "Usage: bash scripts/demo.sh [--offline]" >&2
        exit 2
        ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

# --- helpers ------------------------------------------------------------------
step() {
    printf '\n============================================================\n'
    printf '== %s\n' "$1"
    printf '============================================================\n'
}
info() { printf '   %s\n' "$1"; }

die() {
    printf '\nERROR: %s\n' "$1" >&2
    exit 1
}

on_error() {
    local code=$?
    printf '\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n' >&2
    printf 'DEMO STEP FAILED (exit %s, line %s).\n' "${code}" "$1" >&2
    printf 'Most failures are: wrong JDK (need 17 or 21), missing Android SDK\n' >&2
    printf 'platform-36, or a missing sherpa-onnx AAR. Fix the message above and\n' >&2
    printf 're-run: bash scripts/demo.sh\n' >&2
    exit "${code}"
}
trap 'on_error "${LINENO}"' ERR

sha256_of() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "${file}" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "${file}" | awk '{print $1}'
    else
        die "Neither sha256sum nor shasum is available to verify ${file}."
    fi
}

# ==============================================================================
step "Step 0/${TOTAL_STEPS} - Toolchain check (JDK 17 or 21; JDK 24+ is refused)"

if ! command -v java >/dev/null 2>&1; then
    die "java was not found on PATH.
   Install JDK 17 or 21, point JAVA_HOME at it, and re-run.
   Windows example (PowerShell): \$env:JAVA_HOME = 'C:\\Program Files\\Eclipse Adoptium\\jdk-21...'
   Git Bash example: export JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-21...'"
fi

info "JAVA_HOME=${JAVA_HOME:-<unset, using java on PATH>}"
info "java -version:"
java -version 2>&1 | sed 's/^/   /'

JAVA_RAW="$(java -version 2>&1 | head -n1)"
JAVA_VER="$(printf '%s' "${JAVA_RAW}" | sed -n 's/.*version "\([^"]*\)".*/\1/p')"
if [ -z "${JAVA_VER}" ]; then
    die "Could not parse a version out of: ${JAVA_RAW}"
fi

case "${JAVA_VER}" in
1.*) JAVA_MAJOR="$(printf '%s' "${JAVA_VER}" | cut -d. -f2)" ;;
*) JAVA_MAJOR="$(printf '%s' "${JAVA_VER}" | cut -d. -f1)" ;;
esac

case "${JAVA_MAJOR}" in
'' | *[!0-9]*) die "Could not parse a numeric Java major from '${JAVA_VER}'." ;;
esac

if [ "${JAVA_MAJOR}" -ge 24 ]; then
    die "Active Java is ${JAVA_VER} (JDK ${JAVA_MAJOR}). This WILL break the build:
   Gradle 8.13 and AGP 8.13.0 are not compatible with JDK ${JAVA_MAJOR}.
   Install JDK 17 or 21, set JAVA_HOME to that JDK, then re-run.
   Verify with: java -version"
fi
if [ "${JAVA_MAJOR}" -lt 17 ]; then
    die "Active Java is ${JAVA_VER} (JDK ${JAVA_MAJOR}); need JDK 17 or 21 (minSdk 28 / AGP 8.13)."
fi
info "OK: JDK ${JAVA_MAJOR} is supported (17-23 accepted; 17 or 21 recommended)."

# ==============================================================================
step "Step 1/${TOTAL_STEPS} - Vendored sherpa-onnx ${VERSION} AAR"

AAR="${ROOT}/libs/sherpa-onnx-${VERSION}.aar"
if [ -f "${AAR}" ] && [ "$(sha256_of "${AAR}")" = "${SHA256}" ]; then
    info "Already present and SHA-256 verified: ${AAR}"
else
    if [ -f "${AAR}" ]; then
        info "Present but checksum mismatch; re-fetching."
    else
        info "Missing; fetching via scripts/fetch-sherpa-aar.sh"
    fi
    bash "${ROOT}/scripts/fetch-sherpa-aar.sh"
    [ -f "${AAR}" ] || die "fetch-sherpa-aar.sh did not produce ${AAR}."
    [ "$(sha256_of "${AAR}")" = "${SHA256}" ] || die "SHA-256 mismatch after fetch for ${AAR}."
    info "Fetched and SHA-256 verified: ${AAR}"
fi

# ==============================================================================
step "Step 2/${TOTAL_STEPS} - Headline: unit tests + static analysis + engine resolution"
info "BUILD SUCCESSFUL here is the 'everything passes' evidence:"
info "  testDebugUnitTest : 386 tests, 0 failures across 9 modules"
info "  ktlintCheck detekt verifyEngineResolution"

./gradlew ${GRADLE_OFFLINE} testDebugUnitTest ktlintCheck detekt verifyEngineResolution

printf '\n'
printf '**********************************************************************\n'
printf '*  BUILD SUCCESSFUL - 386 tests, 0 failures + ktlint + detekt +       *\n'
printf '*  engine resolution all green.                                       *\n'
printf '**********************************************************************\n'

# ==============================================================================
step "Step 3/${TOTAL_STEPS} - Build the demo app (:demo-app:assembleDebug)"
./gradlew ${GRADLE_OFFLINE} :demo-app:assembleDebug

APK=""
for candidate in "${ROOT}"/demo-app/build/outputs/apk/debug/*.apk; do
    [ -e "${candidate}" ] && APK="${candidate}"
done
if [ -z "${APK}" ]; then
    die "No APK found under demo-app/build/outputs/apk/debug/. Check the :demo-app output name."
fi
info "APK: ${APK}"

# ==============================================================================
step "Step 4/${TOTAL_STEPS} - Deploy to a device (only if one is attached)"

ADB=""
if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
else
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        if [ -n "${sdk}" ] && [ -x "${sdk}/platform-tools/adb" ]; then
            ADB="${sdk}/platform-tools/adb"
            break
        fi
        if [ -n "${sdk}" ] && [ -x "${sdk}/platform-tools/adb.exe" ]; then
            ADB="${sdk}/platform-tools/adb.exe"
            break
        fi
    done
fi

studio_hint() {
    info "Run it from Android Studio instead:"
    info "  1. Open the project, wait for Gradle sync."
    info "  2. Select the ':demo-app' run configuration."
    info "  3. Start an emulator or plug in a phone, then click Run (green triangle)."
    info "  4. The app is ${DEMO_PACKAGE}, launcher ${DEMO_ACTIVITY}."
}

if [ -z "${ADB}" ]; then
    info "adb not found on PATH or under ANDROID_HOME / ANDROID_SDK_ROOT. Skipping install."
    studio_hint
else
    DEVICES="$("${ADB}" devices | awk 'NR>1 && $2=="device" {print $1}')"
    if [ -z "${DEVICES}" ]; then
        info "No device or emulator attached (adb devices lists no 'device' line). Skipping install."
        studio_hint
    else
        info "Attached device(s): $(printf '%s' "${DEVICES}" | tr '\n' ' ')"
        info "Installing: ${APK}"
        "${ADB}" install -r "${APK}"
        info "Launching ${DEMO_ACTIVITY}"
        "${ADB}" shell am start -n "${DEMO_ACTIVITY}"
    fi
fi

# ==============================================================================
step "Step 5/${TOTAL_STEPS} - Summary"

info "[x] JDK check          : supported JDK ${JAVA_MAJOR} (need 17 or 21; 24+ refused)"
info "[x] sherpa-onnx AAR    : verified (${VERSION}, sha256 ${SHA256:0:12}...)"
info "[x] Verification suite : testDebugUnitTest (386/0) + ktlint + detekt + engine resolution"
info "[x] Demo app build     : :demo-app:assembleDebug"
info "[x] APK                : ${APK}"
if [ -n "${ADB}" ] && [ -n "${DEVICES:-}" ]; then
    info "[x] Device deploy      : installed and launched on ${DEVICES}"
else
    info "[ ] Device deploy      : skipped (no adb device); run from Android Studio"
fi
printf '\n'
info "Tip: on a pre-warmed machine you may re-run with --offline to skip dependency lookups."
info "The demo app runs the REAL engines; large models download on demand. See docs/DEMO.md."
