"""Run the bounded host contract audit for KMK security/privacy boundaries."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from .control_common import SOURCE_ROOT, WORKSPACE_ROOT


def _read(relative: str) -> str:
    return (SOURCE_ROOT / relative).read_text(encoding="utf-8")


def _check(name: str, condition: bool, detail: str) -> dict[str, Any]:
    return {"name": name, "passed": condition, "detail": detail}


def audit() -> dict[str, Any]:
    manifest = _read("app/src/main/AndroidManifest.xml")
    release_network = _read("app/src/main/res/xml/network_security_config.xml")
    debug_network = _read("app/src/debug/res/xml/network_security_config.xml")
    webview = _read("app/src/main/java/eu/kanade/presentation/webview/WebViewScreenContent.kt")
    webview_activity = _read("app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewActivity.kt")
    webview_model = _read("app/src/main/java/eu/kanade/tachiyomi/ui/webview/WebViewScreenModel.kt")
    sanitizer = _read("app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/DeepLinkIntentSanitizer.kt")
    webview_tests = _read("app/src/test/java/eu/kanade/presentation/webview/WebViewUrlPolicyTest.kt")
    sanitizer_tests = _read("app/src/test/java/eu/kanade/tachiyomi/ui/deeplink/DeepLinkIntentSanitizerTest.kt")
    updater = _read("app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateDownloadJob.kt")
    updater_tests = _read("app/src/test/java/eu/kanade/tachiyomi/data/updater/AppUpdateDownloadCancellationTest.kt")
    checks = [
        _check("backup_disabled", 'android:allowBackup="false"' in manifest, "manifest disables app backup"),
        _check("release_cleartext_disabled", 'cleartextTrafficPermitted="false"' in release_network, "release network policy denies cleartext"),
        _check("debug_policy_explicit", 'cleartextTrafficPermitted="true"' in debug_network, "debug exception is isolated to debug resource"),
        _check("webview_url_allowlist", "internal fun isAllowedWebUrl" in webview and "uri.scheme?.lowercase() in setOf(\"http\", \"https\")" in webview, "WebView accepts only HTTP(S) URLs with a host"),
        _check("webview_navigation_rechecks", "if (!isAllowedWebUrl(url)) return true" in webview, "subsequent WebView navigation is rechecked"),
        _check("deep_link_allowlist", "object DeepLinkIntentSanitizer" in sanitizer and "else -> null" in sanitizer, "exported deep-link proxy uses an explicit action allowlist"),
        _check("cookie_log_redaction", 'logcat { "Cleared $cleared WebView cookies" }' in webview_activity and 'logcat { "Cleared $cleared WebView cookies" }' in webview_model, "cookie diagnostics omit the external URL"),
        _check("webview_policy_tests", "rejects non-web" in webview_tests and "cookie cleanup logs" in webview_tests, "host tests cover URL rejection and log privacy"),
        _check("deep_link_policy_tests", "unrecognized custom action" in sanitizer_tests and "arbitrary extras" in sanitizer_tests, "host tests cover malformed/external deep-link inputs"),
        _check(
            "updater_cancellation_propagation",
            "shouldPropagateInstallationCancellation(error)" in updater
            and "throw error" in updater
            and "installation cancellation is propagated" in updater_tests,
            "update installation cancellation rethrows before the ordinary install-error fallback",
        ),
    ]
    return {
        "domain_id": "DOM-SECURITY-PRIVACY",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": ["merged manifest behavior", "runtime WebView navigation", "device logging and release artifact execution"],
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
