"""Run the bounded host contract audit for backup recovery and lifecycle ownership."""

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
    app = _read("app/src/main/java/eu/kanade/tachiyomi/App.kt")
    store = _read("app/src/main/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStore.kt")
    backup_create = _read("app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreateJob.kt")
    backup_restore = _read("app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestoreJob.kt")
    dialog = _read("app/src/main/java/eu/kanade/presentation/components/BackupCleanupRecoveryDialog.kt")
    recovery_tests = _read("app/src/test/java/eu/kanade/tachiyomi/util/export/BackupCleanupRecoveryStoreTest.kt")
    restore_tests = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (SOURCE_ROOT / "app/src/test/java/eu/kanade/tachiyomi/data/backup").rglob("*.kt")
    )

    initialize_marker = "WorkManager.initialize(this, Configuration.Builder().build())"
    reconcile_marker = "BackupCleanupRecoveryStore.reconcileOnStartup"
    checks = [
        _check(
            "workmanager_before_recovery",
            "WorkManager.isInitialized()" in app
            and initialize_marker in app
            and reconcile_marker in app
            and app.index(initialize_marker) < app.index(reconcile_marker),
            "explicit WorkManager fallback completes before process-scoped recovery reconciliation",
        ),
        _check(
            "durable_recovery_record",
            all(marker in store for marker in ("BackupCleanupRecord", "persist", "loadRecord", "reconcileOnStartup")),
            "recovery state is persisted and reloaded through the application-scoped store",
        ),
        _check(
            "unresolved_is_non_destructive",
            "SafArtifactOutcome.UNRESOLVED" in store
            and "deleteSafDocument" not in store
            and "SafArtifactCleanupDialog(" in dialog,
            "unknown outcomes remain distinguishable and are not converted into deletion claims",
        ),
        _check(
            "rollback_boundary",
            "onRemoved = { BackupCleanupRecoveryStore.clear(current.operationId) }" in dialog
            and "onKept = { BackupCleanupRecoveryStore.clear(current.operationId) }" in dialog
            and "onDismissed = { BackupCleanupRecoveryStore.clear(current.operationId) }" in dialog,
            "user Remove/Keep/dismiss actions clear only the matching recovery operation",
        ),
        _check(
            "cancellation_propagation",
            "catch (e: CancellationException)" in store
            and "catch (e: CancellationException)" in backup_create
            and "catch (e: CancellationException)" in backup_restore
            and backup_restore.count("throw e") >= 1,
            "backup creation and restore preserve coroutine cancellation and cleanup in finally",
        ),
        _check(
            "privacy_safe_recovery_logging",
            "logcat(LogPriority.WARN, e)" not in store
            and "logcat(LogPriority.ERROR, e)" not in store
            and "exception-message" in store,
            "recovery diagnostics remain generic and do not log exception payloads or backup contents",
        ),
        _check(
            "host_recovery_tests",
            all(marker in recovery_tests for marker in ("process recreation", "UNRESOLVED", "failWrites", "CancellationException"))
            and "CancellationException" in restore_tests,
            "host tests cover durable recreation, unresolved states, persistence failure, and cancellation",
        ),
    ]
    return {
        "domain_id": "DOM-BACKUP-RECOVERY",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": [
            "AndroidX WorkManager provider versus explicit initialization on device",
            "process death while backup work is active",
            "real DocumentsProvider cleanup and backup restore execution",
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
