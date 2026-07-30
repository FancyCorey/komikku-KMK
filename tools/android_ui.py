#!/usr/bin/env python3
"""Bounded, semantic Android UI navigation for local Komikku development."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable, Optional


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PACKAGE = os.environ.get("ANDROID_APP_PACKAGE", "app.komikku.dev")
ARTIFACT_ROOT = ROOT / "artifacts" / "android"


class UiError(RuntimeError):
    pass


@dataclass(frozen=True)
class Element:
    text: str
    description: str
    resource_id: str
    class_name: str
    clickable: bool
    scrollable: bool
    enabled: bool
    selected: bool
    bounds: str

    def as_dict(self) -> dict[str, object]:
        return {
            "text": self.text or None,
            "description": self.description or None,
            "resource_id": self.resource_id or None,
            "class": self.class_name,
            "clickable": self.clickable,
            "scrollable": self.scrollable,
            "enabled": self.enabled,
            "selected": self.selected,
            "bounds": self.bounds,
        }


def find_adb() -> str:
    configured = os.environ.get("ADB_PATH")
    if configured:
        return configured
    candidates = [
        ROOT / ".tools" / "android-sdk" / "platform-tools" / "adb.exe",
        ROOT / ".tools" / "android-sdk" / "platform-tools" / "adb",
    ]
    for candidate in candidates:
        if candidate.exists():
            return str(candidate)
    found = shutil.which("adb")
    if found:
        return found
    raise UiError("ADB was not found. Set ADB_PATH or install Android platform-tools.")


class Adb:
    def __init__(self, serial: Optional[str], timeout: float = 15.0):
        self.adb = find_adb()
        self.serial = serial or os.environ.get("ANDROID_SERIAL")
        self.timeout = timeout

    def run(self, *args: str, timeout: Optional[float] = None, binary: bool = False) -> str | bytes:
        command = [self.adb]
        if self.serial:
            command += ["-s", self.serial]
        command += list(args)
        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                timeout=timeout or self.timeout,
                text=not binary,
            )
        except subprocess.TimeoutExpired as exc:
            raise UiError(f"Timed out after {timeout or self.timeout:g}s: {' '.join(command)}") from exc
        output = completed.stdout if completed.returncode == 0 else completed.stderr
        if completed.returncode != 0:
            rendered = output.decode(errors="replace") if isinstance(output, bytes) else output
            raise UiError(f"ADB failed ({completed.returncode}): {rendered.strip()}")
        return output

    def shell(self, *args: str, timeout: Optional[float] = None) -> str:
        return str(self.run("shell", *args, timeout=timeout))

    def ensure_target(self) -> str:
        raw = str(self.run("devices"))
        devices = [line.split("\t", 1)[0] for line in raw.splitlines() if "\tdevice" in line]
        if self.serial:
            if self.serial not in devices:
                raise UiError(f"Requested device {self.serial!r} is not authorized and online.")
            return self.serial
        if len(devices) != 1:
            raise UiError("Expected exactly one authorized device; found: " + ", ".join(devices or ["none"]))
        self.serial = devices[0]
        return self.serial

    def ui_xml(self) -> str:
        self.ensure_target()
        remote = "/sdcard/codex_window.xml"
        self.shell("uiautomator", "dump", remote, timeout=20)
        return self.shell("cat", remote)

    def elements(self) -> list[Element]:
        try:
            root = ET.fromstring(self.ui_xml())
        except ET.ParseError as exc:
            raise UiError("The device returned an invalid UI hierarchy.") from exc
        result = []
        for node in root.iter("node"):
            values = node.attrib
            text = values.get("text", "").strip()
            description = values.get("content-desc", "").strip()
            resource_id = values.get("resource-id", "").strip()
            enabled = values.get("enabled", "false") == "true"
            clickable = values.get("clickable", "false") == "true"
            scrollable = values.get("scrollable", "false") == "true"
            if not enabled or not (text or description or resource_id or clickable or scrollable):
                continue
            result.append(
                Element(
                    text=text,
                    description=description,
                    resource_id=resource_id,
                    class_name=values.get("class", ""),
                    clickable=clickable,
                    scrollable=scrollable,
                    enabled=enabled,
                    selected=values.get("selected", "false") == "true",
                    bounds=values.get("bounds", ""),
                ),
            )
        return result


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        raise UiError(f"Unsupported bounds: {bounds!r}")
    return tuple(int(value) for value in match.groups())  # type: ignore[return-value]


def matches(element: Element, selector: str, value: str, partial: bool = False) -> bool:
    field = {
        "id": element.resource_id,
        "description": element.description,
        "text": element.text,
    }[selector]
    return value.lower() in field.lower() if partial else field == value


def select_element(elements: Iterable[Element], selector: str, value: str, partial: bool = False) -> Element:
    found = [element for element in elements if matches(element, selector, value, partial)]
    if not found:
        raise UiError(f"No enabled element matched {selector}={value!r}.")
    if len(found) > 1:
        raise UiError(f"Selector {selector}={value!r} matched {len(found)} elements; use a stable ID or exact selector.")
    return found[0]


def tap_element(adb: Adb, element: Element) -> None:
    left, top, right, bottom = parse_bounds(element.bounds)
    adb.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def wait_for(adb: Adb, selector: str, value: str, timeout: float = 10.0, partial: bool = False) -> Element:
    deadline = time.monotonic() + timeout
    last_error = ""
    while time.monotonic() < deadline:
        try:
            return select_element(adb.elements(), selector, value, partial)
        except UiError as exc:
            last_error = str(exc)
            time.sleep(0.25)
    raise UiError(f"Timed out waiting for {selector}={value!r}. {last_error}")


def click(adb: Adb, selector: str, value: str, partial: bool = False, wait: Optional[tuple[str, str]] = None) -> None:
    tap_element(adb, select_element(adb.elements(), selector, value, partial))
    if wait:
        wait_for(adb, wait[0], wait[1])


def scroll_to_text(adb: Adb, text: str, click_target: bool, max_swipes: int) -> None:
    for _ in range(max_swipes + 1):
        try:
            element = select_element(adb.elements(), "text", text)
            if click_target:
                tap_element(adb, element)
            return
        except UiError:
            scrollables = [element for element in adb.elements() if element.scrollable]
            if not scrollables:
                raise UiError(f"No scrollable container found while searching for {text!r}.")
            left, top, right, bottom = parse_bounds(scrollables[0].bounds)
            x = (left + right) // 2
            adb.shell("input", "swipe", str(x), str(bottom - 160), str(x), str(top + 160), "450")
    raise UiError(f"Could not find {text!r} after {max_swipes} bounded swipes.")


def package_activity(adb: Adb, package: str) -> str:
    output = adb.shell("cmd", "package", "resolve-activity", "--brief", package)
    for line in reversed(output.splitlines()):
        if "/" in line:
            return line.strip()
    raise UiError(f"Could not resolve a launchable activity for {package}.")


def load_routes() -> dict[str, dict]:
    path = ROOT / "tools" / "routes.json"
    if not path.exists():
        raise UiError(f"Route file does not exist: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def run_route(adb: Adb, name: str) -> None:
    routes = load_routes()
    if name not in routes:
        raise UiError(f"Unknown route {name!r}. Available: {', '.join(sorted(routes))}")
    for index, step in enumerate(routes[name]["steps"], start=1):
        action = step["action"]
        try:
            if action == "click-id":
                click(adb, "id", step["value"])
            elif action == "click-text":
                click(adb, "text", step["value"])
            elif action == "click-description":
                click(adb, "description", step["value"])
            elif action == "scroll-to-text":
                scroll_to_text(adb, step["value"], step.get("click", False), step.get("max_swipes", 8))
            elif action == "back":
                adb.shell("input", "keyevent", "4")
            else:
                raise UiError(f"Unsupported route action {action!r}.")
        except UiError as exc:
            raise UiError(f"Route {name!r} stopped at step {index}: {exc}") from exc
        time.sleep(0.25)
    verify = routes[name].get("verify")
    if verify:
        wait_for(adb, verify["selector"], verify["value"], verify.get("timeout", 10), verify.get("partial", False))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="Explicit device serial; otherwise use ANDROID_SERIAL or one online device")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help=f"Application package (default: {DEFAULT_PACKAGE})")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("status")
    sub.add_parser("current-activity")
    sub.add_parser("elements", aliases=["list-elements"])
    sub.add_parser("dump")
    screenshot = sub.add_parser("screenshot")
    screenshot.add_argument("--output")
    for name, selector in (("click-id", "id"), ("click-text", "text"), ("click-description", "description")):
        command = sub.add_parser(name)
        command.add_argument("value")
        command.add_argument("--partial", action="store_true")
    scroll = sub.add_parser("scroll-to-text")
    scroll.add_argument("value")
    scroll.add_argument("--click", action="store_true")
    scroll.add_argument("--max-swipes", type=int, default=8)
    swipe = sub.add_parser("swipe")
    swipe.add_argument("direction", choices=("up", "down", "left", "right"))
    swipe.add_argument("--amount", type=float, default=0.7)
    input_parser = sub.add_parser("input")
    input_parser.add_argument("value")
    input_parser.add_argument("--clear", action="store_true")
    sub.add_parser("back")
    sub.add_parser("home")
    start = sub.add_parser("app-start")
    start.add_argument("--package", default=DEFAULT_PACKAGE)
    stop = sub.add_parser("app-stop")
    stop.add_argument("--package", default=DEFAULT_PACKAGE)
    assertion = sub.add_parser("assert-visible")
    assertion.add_argument("--id")
    assertion.add_argument("--text")
    assertion.add_argument("--description")
    assertion.add_argument("--partial", action="store_true")
    route = sub.add_parser("route")
    route.add_argument("name")
    return parser


def main(argv: Optional[list[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    adb = Adb(args.serial)
    try:
        if args.command == "status":
            print(json.dumps({"serial": adb.ensure_target(), "package": args.package}, indent=2))
        elif args.command == "current-activity":
            adb.ensure_target()
            print(adb.shell("dumpsys", "activity", "activities", timeout=20).split("mResumedActivity", 1)[-1].splitlines()[0].strip())
        elif args.command in ("elements", "list-elements"):
            print(json.dumps([element.as_dict() for element in adb.elements()], ensure_ascii=False, indent=2))
        elif args.command == "dump":
            print(adb.ui_xml())
        elif args.command == "screenshot":
            adb.ensure_target()
            output = Path(args.output) if args.output else ARTIFACT_ROOT / "screenshots" / f"screen-{datetime.now():%Y%m%d-%H%M%S}.png"
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(adb.run("exec-out", "screencap", "-p", binary=True))
            print(output)
        elif args.command in ("click-id", "click-text", "click-description"):
            click(adb, {"click-id": "id", "click-text": "text", "click-description": "description"}[args.command], args.value, args.partial)
        elif args.command == "scroll-to-text":
            scroll_to_text(adb, args.value, args.click, args.max_swipes)
        elif args.command == "swipe":
            amount = max(0.1, min(args.amount, 0.9))
            width, height = 1000, 1600
            if args.direction == "up":
                coords = (500, int(height * (0.5 + amount / 2)), 500, int(height * (0.5 - amount / 2)))
            elif args.direction == "down":
                coords = (500, int(height * (0.5 - amount / 2)), 500, int(height * (0.5 + amount / 2)))
            elif args.direction == "left":
                coords = (int(width * (0.5 + amount / 2)), 800, int(width * (0.5 - amount / 2)), 800)
            else:
                coords = (int(width * (0.5 - amount / 2)), 800, int(width * (0.5 + amount / 2)), 800)
            adb.shell("input", "swipe", *(str(value) for value in coords), "450")
        elif args.command == "input":
            if args.clear:
                adb.shell("input", "keyevent", "123")
            adb.shell("input", "text", args.value.replace(" ", "%s"))
        elif args.command == "back":
            adb.shell("input", "keyevent", "4")
        elif args.command == "home":
            adb.shell("input", "keyevent", "3")
        elif args.command == "app-start":
            adb.ensure_target()
            adb.shell("monkey", "-p", args.package, "1")
            print(package_activity(adb, args.package))
        elif args.command == "app-stop":
            adb.ensure_target()
            adb.shell("am", "force-stop", args.package)
        elif args.command == "assert-visible":
            selectors = [("id", args.id), ("text", args.text), ("description", args.description)]
            selector, value = next(((key, value) for key, value in selectors if value), (None, None))
            if not selector:
                raise UiError("Provide exactly one of --id, --text, or --description.")
            select_element(adb.elements(), selector, value, args.partial)
            print("ok")
        elif args.command == "route":
            run_route(adb, args.name)
            print("ok")
        return 0
    except UiError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
