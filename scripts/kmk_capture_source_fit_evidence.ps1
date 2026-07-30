<#
.SYNOPSIS
    Captures a timestamped raw screenshot from a connected Android device via ADB, for building the
    public Source Evaluation / source-fit evidence package described in
    docs/community/KMK_SOURCE_EVALUATION_SCREENSHOT_EVIDENCE_WORKFLOW.md.

.DESCRIPTION
    This script only captures and stores a RAW screenshot locally. It does NOT sanitize, blur, redact,
    or otherwise modify the image, and it does NOT publish or attach anything anywhere. Automatic
    redaction is deliberately not implemented here: this script cannot reliably detect which on-screen
    text is a private source/repo name versus ordinary UI chrome, and a wrong automatic redaction is
    worse than no redaction (it creates false confidence). Every raw screenshot requires a manual
    sanitization pass before any public use -- see the printed next-steps checklist at the end of this
    script, and the full guide linked above.

    Raw captures go to a "raw" subfolder (private, never for publishing). Sanitized copies always go to
    a separate "sanitized" subfolder that this script does not write to -- sanitization is a manual step
    performed by a human in an image editor, described in the guide.

.PARAMETER Label
    Short, purpose-describing label for this capture, e.g. "source-fit-good", "source-fit-low-confidence".
    Used in the raw filename. Defaults to "capture" if omitted. Do not put source/repo names in the label
    if you intend the eventual sanitized copy to be posted publicly -- keep labels behavior-descriptive,
    not identity-descriptive (see the guide's captioning rules for why).

.PARAMETER EvidenceRoot
    Root folder for the local evidence workspace. Defaults to a folder under this repo's own
    docs/community/evidence, which is already treated as local-only (see the guide). Override if you'd
    rather keep evidence entirely outside the repo tree.

.PARAMETER AdbPath
    Path to adb.exe. Defaults to the project's own bundled Android SDK platform-tools, per the project's
    established convention.

.EXAMPLE
    ./scripts/kmk_capture_source_fit_evidence.ps1 -Label "source-fit-good"

    Captures one screenshot from the connected device and saves it as
    docs/community/evidence/raw/2026-07-20_211530_source-fit-good.png

.NOTES
    This script intentionally never overwrites an existing file -- if a collision would occur (same
    label captured within the same second), it appends a numeric suffix instead of clobbering.
    This script does not install anything, does not modify the connected device beyond taking a
    screenshot, and does not read/write any file outside the evidence folder it creates.
#>

param(
    [string]$Label = "capture",
    [string]$EvidenceRoot = (Join-Path $PSScriptRoot "..\docs\community\evidence"),
    [string]$AdbPath = (Join-Path $PSScriptRoot "..\.tools\android-sdk\platform-tools\adb.exe")
)

$ErrorActionPreference = "Stop"

function Write-Section($text) {
    Write-Output ""
    Write-Output "== $text =="
}

# --- Resolve paths -----------------------------------------------------------------------------------
$EvidenceRoot = (Resolve-Path -Path $EvidenceRoot -ErrorAction SilentlyContinue)
if (-not $EvidenceRoot) {
    # Resolve-Path fails on a path that doesn't exist yet; fall back to the literal join.
    $EvidenceRoot = Join-Path $PSScriptRoot "..\docs\community\evidence"
}
$RawDir = Join-Path $EvidenceRoot "raw"
$SanitizedDir = Join-Path $EvidenceRoot "sanitized"

if (-not (Test-Path $RawDir)) {
    New-Item -ItemType Directory -Force -Path $RawDir | Out-Null
}
if (-not (Test-Path $SanitizedDir)) {
    # Created up front so the folder structure is visible immediately, even though this script never
    # writes into it -- sanitized copies are always a manual step.
    New-Item -ItemType Directory -Force -Path $SanitizedDir | Out-Null
}

if (-not (Test-Path $AdbPath)) {
    Write-Error "adb.exe not found at expected path: $AdbPath`nPass -AdbPath explicitly if your checkout differs."
}

# --- Check device connectivity -------------------------------------------------------------------------
Write-Section "Checking connected devices"
$devicesOutput = & $AdbPath devices
Write-Output $devicesOutput
$deviceLines = $devicesOutput | Select-String -Pattern "\tdevice$"
if (-not $deviceLines) {
    Write-Error "No connected device found (looked for a line ending in '`tdevice'). Connect a device with USB debugging enabled and retry."
}

# --- Build a collision-safe raw filename ---------------------------------------------------------------
$timestamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$safeLabel = ($Label -replace '[^a-zA-Z0-9\-_]', '_')
$baseName = "${timestamp}_${safeLabel}"
$rawFile = Join-Path $RawDir "$baseName.png"
$suffix = 1
while (Test-Path $rawFile) {
    # Never overwrite an existing capture -- append a numeric suffix instead.
    $rawFile = Join-Path $RawDir "${baseName}_$suffix.png"
    $suffix++
}

# --- Capture ------------------------------------------------------------------------------------------
Write-Section "Capturing screenshot"
Write-Output "Navigate the device to the exact screen you want to document, then this script will capture it."
Write-Output "Saving to: $rawFile"

# exec-out streams the PNG bytes directly, avoiding an extra on-device file + pull step.
& $AdbPath exec-out screencap -p > $rawFile

if (-not (Test-Path $rawFile) -or (Get-Item $rawFile).Length -eq 0) {
    Write-Error "Capture failed or produced an empty file: $rawFile"
}

Write-Output "Captured: $rawFile ($((Get-Item $rawFile).Length) bytes)"

# --- Next steps -----------------------------------------------------------------------------------------
Write-Section "This is a RAW capture. It is NOT safe to post publicly as-is."
Write-Output @"
Manual redaction steps required before any public use (see
docs/community/KMK_SOURCE_EVALUATION_SCREENSHOT_EVIDENCE_WORKFLOW.md for the full checklist):

  1. Open the raw file in an image editor:
       $rawFile

  2. Blur or crop out anything on the checklist, at minimum:
       - source/site names that should not be exposed
       - any private repo name or private extension-repo URL
       - any package/application ID identifying a private repo or fork
       - usernames, Discord handles, personal device identifiers
       - notification-bar / status-bar details (battery %, carrier, time, notification icons)
       - private source lists (show only the row(s) needed to make the point)
       - explicit/lewd source names, unless the point genuinely requires one
       - personal manga lists/ratings not chosen to be shared

  3. Save the sanitized copy into:
       $SanitizedDir
     using a purpose-based filename, e.g. source-fit-good-sanitized.png -- not the raw capture-order name.

  4. Write a caption for the sanitized image before attaching it anywhere:
       - what behavior the image demonstrates
       - what was censored
     Do not post an image without a caption.

  5. Raw files under raw/ are local-only. Never attach them to a public issue, PR, Discord message, or
     Reddit post -- not even temporarily. Only files from sanitized/ that have been through step 2-4 are
     safe to post, and only after a human review pass -- this script performs no automatic redaction.
"@
