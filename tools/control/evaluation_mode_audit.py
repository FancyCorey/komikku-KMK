"""Run the bounded host contract audit for Evaluation Mode presentation."""

from __future__ import annotations

import argparse
import json
from typing import Any

from .control_common import SOURCE_ROOT, WORKSPACE_ROOT


def _read(relative: str) -> str:
    return (SOURCE_ROOT / relative).read_text(encoding="utf-8")


def _check(name: str, condition: bool, detail: str) -> dict[str, Any]:
    return {"name": name, "passed": condition, "detail": detail}


def audit() -> dict[str, Any]:
    screen = _read("app/src/main/java/exh/recs/evaluation/SourceEvaluationScreen.kt")
    error_policy = _read(
        "app/src/main/java/exh/recs/evaluation/SourceEvaluationErrorDisplayPolicy.kt"
    )
    diagnostics_policy = _read(
        "app/src/main/java/exh/recs/evaluation/SourceEvaluationDiagnosticsPolicy.kt"
    )
    diagnostics_builder = _read(
        "app/src/main/java/exh/recs/evaluation/SourceEvaluationDiagnosticsBuilder.kt"
    )
    screen_model = _read("app/src/main/java/exh/recs/evaluation/SourceEvaluationScreenModel.kt")
    job = _read("app/src/main/java/exh/recs/evaluation/SourceEvaluationJob.kt")
    policy_tests = _read(
        "app/src/test/java/exh/recs/evaluation/SourceEvaluationErrorDisplayPolicyTest.kt"
    )
    diagnostics_tests = _read(
        "app/src/test/java/exh/recs/evaluation/SourceEvaluationDiagnosticsPolicyTest.kt"
    )
    cancellation_tests = _read(
        "app/src/test/java/exh/recs/evaluation/SourceEvaluationJobCancellationTest.kt"
    )

    checks = [
        _check(
            "crash_recovery_identity_policy",
            "SourceEvaluationErrorDisplayPolicy.crashRecoveryDisplay" in screen
            and 'EvaluationModeFormatter.sourceLabel("ext:$extensionName")' in error_policy,
            "crash-recovery UI uses the same stable relabeling boundary as other Evaluation Mode identities",
        ),
        _check(
            "original_mode_preserved",
            "evaluationModeEnabled: Boolean" in error_policy
            and "extensionLabel = if (evaluationModeEnabled)" in error_policy
            and "extensionName" in error_policy,
            "disabled mode keeps the original extension label and phase",
        ),
        _check(
            "privacy_diagnostics_policy",
            "SourceEvaluationDiagnosticsPolicy.sanitize" in diagnostics_builder
            and "errorCategoryLabel" in diagnostics_policy
            and "marker.extensionPkgName" in diagnostics_policy
            and "marker.sourceId" in diagnostics_policy,
            "clipboard diagnostics remain independently sanitized for names, identifiers, and errors",
        ),
        _check(
            "cancellation_propagation",
            "catch (e: CancellationException)" in job
            and "throw e" in job
            and "finally" in job
            and "CancellationException" in cancellation_tests,
            "evaluation cancellation remains distinct from ordinary failure and still cleans up",
        ),
        _check(
            "startup_recovery_boundary",
            "SourceEvaluationStartupRecovery" in screen_model
            and "ScreenErrorKey.CrashRecovery" in screen_model,
            "crash recovery remains owned by the existing ScreenModel startup path",
        ),
        _check(
            "focused_privacy_tests",
            all(marker in diagnostics_tests for marker in ("raw error text", "signature hash", "evaluation mode"))
            and all(marker in policy_tests for marker in ("mode off", "mode on", "phase")),
            "focused tests cover privacy, compatibility, and phase preservation",
        ),
        _check(
            "existing_ui_lifecycle_integration",
            all(marker in screen for marker in ("rememberScreenModel", "DisposableEffect", "onStart"))
            and "rememberEvaluationModeEnabled" in screen,
            "the existing Source Evaluation route, lifecycle, and preference-backed display state remain in use",
        ),
    ]
    return {
        "domain_id": "DOM-EVALUATION-MODE",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": [
            "crash-recovery rendering and Evaluation Mode preference changes on a real device",
            "process death during extension evaluation and subsequent startup recovery",
            "real installer, Shizuku, and extension repository behavior",
        ],
        "workspace_root": str(WORKSPACE_ROOT),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args(argv)
    result = audit()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["host_contract_valid"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
