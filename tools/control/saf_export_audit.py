"""Run the bounded host contract audit for SAF/export and backup recovery."""

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
    coordinator = _read("app/src/main/java/eu/kanade/tachiyomi/util/export/SafExportCoordinator.kt")
    library_exporter = _read("app/src/main/java/eu/kanade/tachiyomi/data/export/LibraryExporter.kt")
    create_backup = _read("app/src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt")
    restore_backup = _read("app/src/main/java/eu/kanade/presentation/more/settings/screen/data/RestoreBackupScreen.kt")
    recovery_store = _read("app/src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt")
    coordinator_tests = _read("app/src/test/java/eu/kanade/tachiyomi/util/export/SafExportCoordinatorTest.kt")
    recovery_tests = _read("app/src/test/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStoreTest.kt")
    library_tests = _read("app/src/test/java/eu/kanade/tachiyomi/data/export/LibraryExporterTest.kt")
    backup_mapping_tests = _read(
        "app/src/test/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreenBackupJobOutcomeTest.kt"
    )

    # This is intentionally a source/test contract audit. It does not claim that a
    # DocumentsProvider, WorkManager job, or lifecycle transition was exercised.
    checks = [
        _check(
            "uri_only_deletion",
            "fun deleteSafDocument(context: Context, uri: Uri)" in coordinator
            and "DocumentsContract.deleteDocument(context.contentResolver, uri)" in coordinator
            and "File(" not in coordinator,
            "cleanup deletion accepts one SAF Uri and delegates to DocumentsContract",
        ),
        _check(
            "uri_validation",
            "isValidSafDocumentUriString" in recovery_store
            and '"content".equals(uri.scheme' in recovery_store
            and 'segments[0] == "document"' in recovery_store
            and 'segments[0] == "tree"' in recovery_store
            and 'segments[2] == "document"' in recovery_store,
            "durable recovery records validate content document URIs and reject file URIs",
        ),
        _check(
            "truthful_outcomes",
            all(token in coordinator for token in ("IN_PROGRESS", "SUCCESS", "FAILED", "CANCELLED", "UNRESOLVED"))
            and "else -> SafArtifactOutcome.UNRESOLVED" in create_backup,
            "unknown backup completion is unresolved; known success/failure/cancellation remain distinct",
        ),
        _check(
            "cleanup_truthfulness",
            "UnregisterableUriOutcome" in coordinator
            and "RETAINED_FOR_CLEANUP" in coordinator
            and "UNRECOVERABLE" in coordinator,
            "failed defensive deletion is retained or reported unrecoverable rather than reported as deleted",
        ),
        _check(
            "failure_cancellation_propagation",
            "catch (e: CancellationException)" in coordinator
            and "throw e" in coordinator
            and "ExportOutcome.WriteFailed" in library_exporter,
            "cancellation propagates and ordinary destination/write failures are classified",
        ),
        _check(
            "lifecycle_ownership",
            "ProcessLifecycleOwner.get().lifecycleScope.launch" in create_backup
            and "rememberScreenModel" in restore_backup
            and "BackupCleanupRecoveryStore" in create_backup,
            "backup recovery outlives the screen while restore state remains screen-model owned",
        ),
        _check(
            "restore_error_privacy",
            "appendLine(error.toString())" not in restore_backup
            and "stringResource(MR.strings.unknown_error)" in restore_backup,
            "restore fallback uses a localized generic error instead of raw diagnostic text",
        ),
        _check(
            "host_tests_cover_contract",
            all(
                marker in coordinator_tests
                for marker in ("UNRESOLVED", "CancellationException", "deleteDocument")
            )
            and all(marker in recovery_tests for marker in ("process recreation", "UNRESOLVED", "URI"))
            and all(marker in library_tests for marker in ("WriteFailed", "CancellationException"))
            and "UNRESOLVED" in backup_mapping_tests,
            "focused host tests cover URI cleanup, recovery uncertainty, failure, and cancellation",
        ),
    ]
    return {
        "domain_id": "DOM-SAF-EXPORT",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": [
            "DocumentsProvider permission and deletion behavior",
            "WorkManager execution and pruning/reconciliation",
            "process death and picker cancellation lifecycle",
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
