#!/usr/bin/env python3
"""Deterministic, bounded Android UI navigation for Komikku development."""

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
from pathlib import Path
from typing import Iterable, Optional

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PACKAGE = os.environ.get("ANDROID_APP_PACKAGE", "app.komikku.dev")


class AndroidUiError(Exception):
    exit_code = 1


class DeviceNotFoundError(AndroidUiError):
    exit_code = 3


class MultipleDevicesError(AndroidUiError):
    exit_code = 3


class AdbTransportError(AndroidUiError):
    exit_code = 1


class HierarchyError(AndroidUiError):
    exit_code = 1


class InvalidHierarchyError(HierarchyError):
    pass


class SelectorError(AndroidUiError):
    exit_code = 4


class InvalidSelectorError(SelectorError):
    pass


class ElementNotFoundError(SelectorError):
    pass


class AmbiguousSelectorError(SelectorError):
    pass


class InvalidBoundsError(AndroidUiError):
    pass


class ActionVerificationError(AndroidUiError):
    exit_code = 5


class RouteValidationError(AndroidUiError):
    exit_code = 6


class RouteExecutionError(AndroidUiError):
    exit_code = 6


@dataclass(frozen=True)
class Bounds:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top

    @property
    def center_x(self) -> int:
        return (self.left + self.right) // 2

    @property
    def center_y(self) -> int:
        return (self.top + self.bottom) // 2

    @property
    def area(self) -> int:
        return self.width * self.height

    def contains(self, other: "Bounds") -> bool:
        return self.left <= other.left and self.top <= other.top and self.right >= other.right and self.bottom >= other.bottom

    @classmethod
    def parse(cls, value: str) -> "Bounds":
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value)
        if not match:
            raise InvalidBoundsError(f"Malformed bounds: {value!r}")
        left, top, right, bottom = (int(item) for item in match.groups())
        if right <= left or bottom <= top:
            raise InvalidBoundsError(f"Inverted or zero-size bounds: {value!r}")
        return cls(left, top, right, bottom)

    def as_string(self) -> str:
        return f"[{self.left},{self.top}][{self.right},{self.bottom}]"


@dataclass(frozen=True)
class UiElement:
    index: int
    text: str
    description: str
    resource_id: str
    class_name: str
    package_name: str
    clickable: bool
    long_clickable: bool
    scrollable: bool
    enabled: bool
    focusable: bool
    focused: bool
    selected: bool
    checked: bool
    checkable: bool
    bounds: Bounds
    parent_index: Optional[int]
    depth: int

    @property
    def actionable(self) -> bool:
        return self.clickable or self.long_clickable or self.focusable or self.checkable or self.scrollable

    def as_dict(self) -> dict[str, object]:
        return {
            "index": self.index,
            "text": self.text or None,
            "description": self.description or None,
            "resource_id": self.resource_id or None,
            "class": self.class_name or None,
            "package": self.package_name or None,
            "clickable": self.clickable,
            "long_clickable": self.long_clickable,
            "scrollable": self.scrollable,
            "enabled": self.enabled,
            "focusable": self.focusable,
            "focused": self.focused,
            "selected": self.selected,
            "checked": self.checked,
            "checkable": self.checkable,
            "bounds": self.bounds.as_string(),
            "parent_index": self.parent_index,
            "depth": self.depth,
        }


@dataclass(frozen=True)
class DisplayInfo:
    width: int
    height: int
    orientation: str


@dataclass(frozen=True)
class Selector:
    text: Optional[str] = None
    description: Optional[str] = None
    resource_id: Optional[str] = None
    class_name: Optional[str] = None
    package_name: Optional[str] = None
    clickable: Optional[bool] = None
    scrollable: Optional[bool] = None
    selected: Optional[bool] = None
    checked: Optional[bool] = None
    focusable: Optional[bool] = None
    parent_text: Optional[str] = None
    ancestor_text: Optional[str] = None
    region: Optional[Bounds] = None
    occurrence: Optional[int] = None
    partial: bool = False

    def validate(self) -> None:
        primary = [self.text, self.description, self.resource_id]
        if sum(value is not None for value in primary) != 1:
            raise InvalidSelectorError("A selector needs exactly one of text, description, or resource_id.")
        if self.occurrence is not None and self.occurrence < 0:
            raise InvalidSelectorError("Selector occurrence cannot be negative.")


def find_adb() -> str:
    configured = os.environ.get("ADB_PATH")
    if configured:
        return configured
    for path in (
        ROOT / ".tools" / "android-sdk" / "platform-tools" / "adb.exe",
        ROOT / ".tools" / "android-sdk" / "platform-tools" / "adb",
    ):
        if path.exists():
            return str(path)
    found = shutil.which("adb")
    if found:
        return found
    raise DeviceNotFoundError("ADB was not found. Set ADB_PATH or install platform-tools.")


class Adb:
    def __init__(self, serial: Optional[str] = None, timeout: float = 15.0):
        self.adb = find_adb()
        self.serial = serial or os.environ.get("ANDROID_SERIAL")
        self.timeout = timeout
        self._display: Optional[DisplayInfo] = None

    def run(self, *args: str, timeout: Optional[float] = None, binary: bool = False) -> str | bytes:
        command = [self.adb] + (["-s", self.serial] if self.serial else []) + list(args)
        try:
            result = subprocess.run(command, capture_output=True, check=False, timeout=timeout or self.timeout, text=not binary)
        except subprocess.TimeoutExpired as exc:
            raise AdbTransportError(f"ADB timed out: {' '.join(command)}") from exc
        if result.returncode != 0:
            output = result.stderr.decode(errors="replace") if binary else result.stderr
            raise AdbTransportError(f"ADB failed ({result.returncode}): {output.strip()}")
        return result.stdout

    def shell(self, *args: str, timeout: Optional[float] = None) -> str:
        return self.run("shell", *args, timeout=timeout)  # type: ignore[return-value]

    def ensure_target(self) -> str:
        lines = str(self.run("devices")).splitlines()
        devices = [line.split("\t", 1)[0] for line in lines if "\tdevice" in line]
        if self.serial:
            if self.serial not in devices:
                raise DeviceNotFoundError(f"Requested device {self.serial!r} is not online and authorized.")
            return self.serial
        if not devices:
            raise DeviceNotFoundError("No authorized Android device is online.")
        if len(devices) > 1:
            raise MultipleDevicesError("Multiple devices are online; set ANDROID_SERIAL or --serial.")
        self.serial = devices[0]
        return self.serial

    def display(self) -> DisplayInfo:
        if self._display:
            return self._display
        output = self.shell("wm", "size")
        sizes = re.findall(r"(\d+)x(\d+)", output)
        if not sizes:
            raise AdbTransportError(f"Could not parse display size from: {output.strip()}")
        width, height = (int(value) for value in sizes[-1])
        orientation = "landscape" if width > height else "portrait"
        self._display = DisplayInfo(width, height, orientation)
        return self._display

    def hierarchy(self) -> str:
        self.ensure_target()
        self.shell("uiautomator", "dump", "/sdcard/codex_window.xml", timeout=20)
        return self.shell("cat", "/sdcard/codex_window.xml")

    def elements(self, include_all: bool = False) -> list[UiElement]:
        try:
            root = ET.fromstring(self.hierarchy())
        except ET.ParseError as exc:
            raise InvalidHierarchyError("Android returned malformed UI XML.") from exc
        elements: list[UiElement] = []

        def walk(node: ET.Element, parent: Optional[UiElement], depth: int) -> None:
            values = node.attrib
            try:
                bounds = Bounds.parse(values.get("bounds", ""))
            except InvalidBoundsError:
                if include_all:
                    raise
                bounds = None
            item = None
            if bounds:
                item = UiElement(
                    index=len(elements),
                    text=values.get("text", "").strip(),
                    description=values.get("content-desc", "").strip(),
                    resource_id=values.get("resource-id", "").strip(),
                    class_name=values.get("class", ""),
                    package_name=values.get("package", ""),
                    clickable=values.get("clickable") == "true",
                    long_clickable=values.get("long-clickable") == "true",
                    scrollable=values.get("scrollable") == "true",
                    enabled=values.get("enabled") == "true",
                    focusable=values.get("focusable") == "true",
                    focused=values.get("focused") == "true",
                    selected=values.get("selected") == "true",
                    checked=values.get("checked") == "true",
                    checkable=values.get("checkable") == "true",
                    bounds=bounds,
                    parent_index=parent.index if parent else None,
                    depth=depth,
                )
                if include_all or (item.enabled and item.bounds.area > 0 and (item.actionable or item.text or item.description)):
                    elements.append(item)
                else:
                    item = None
            for child in node:
                walk(child, item or parent, depth + 1)

        walk(root, None, 0)
        return deduplicate(elements) if not include_all else elements


def deduplicate(elements: list[UiElement]) -> list[UiElement]:
    result: list[UiElement] = []
    seen: set[tuple[object, ...]] = set()
    for element in elements:
        key = (element.text, element.description, element.resource_id, element.class_name, element.bounds.as_string(), element.clickable, element.selected)
        if key in seen:
            continue
        seen.add(key)
        result.append(element)
    return [
        UiElement(
            index=index,
            text=item.text,
            description=item.description,
            resource_id=item.resource_id,
            class_name=item.class_name,
            package_name=item.package_name,
            clickable=item.clickable,
            long_clickable=item.long_clickable,
            scrollable=item.scrollable,
            enabled=item.enabled,
            focusable=item.focusable,
            focused=item.focused,
            selected=item.selected,
            checked=item.checked,
            checkable=item.checkable,
            bounds=item.bounds,
            parent_index=item.parent_index,
            depth=item.depth,
        )
        for index, item in enumerate(result)
    ]


def field_match(actual: str, expected: str, partial: bool) -> bool:
    return expected.lower() in actual.lower() if partial else actual == expected


def resolve(elements: Iterable[UiElement], selector: Selector) -> UiElement:
    selector.validate()
    candidates = list(elements)
    if selector.text is not None:
        candidates = [item for item in candidates if field_match(item.text, selector.text, selector.partial)]
    if selector.description is not None:
        candidates = [item for item in candidates if field_match(item.description, selector.description, selector.partial)]
    if selector.resource_id is not None:
        candidates = [item for item in candidates if field_match(item.resource_id, selector.resource_id, selector.partial)]
    for attr, expected in (("class_name", selector.class_name), ("package_name", selector.package_name), ("clickable", selector.clickable), ("scrollable", selector.scrollable), ("selected", selector.selected), ("checked", selector.checked), ("focusable", selector.focusable)):
        if expected is not None:
            candidates = [item for item in candidates if getattr(item, attr) == expected]
    if selector.region:
        candidates = [item for item in candidates if selector.region.contains(item.bounds) or item.bounds.contains(selector.region)]
    if selector.parent_text:
        candidates = [item for item in candidates if any(field_match(parent.text, selector.parent_text, selector.partial) for parent in elements if parent.index == item.parent_index)]
    if selector.occurrence is not None:
        if selector.occurrence >= len(candidates):
            raise ElementNotFoundError(f"Occurrence {selector.occurrence} is outside {len(candidates)} matches.")
        return candidates[selector.occurrence]
    if not candidates:
        raise ElementNotFoundError("Selector did not match any enabled element.")
    if len(candidates) > 1:
        details = "; ".join(f"{item.index}:{item.bounds.as_string()} class={item.class_name!r} clickable={item.clickable}" for item in candidates[:8])
        raise AmbiguousSelectorError(f"Selector matched {len(candidates)} elements. Candidates: {details}")
    return candidates[0]


def selector_from_args(args: argparse.Namespace) -> Selector:
    region = None
    if any(value is not None for value in (args.within_left, args.within_top, args.within_right, args.within_bottom)):
        if not all(value is not None for value in (args.within_left, args.within_top, args.within_right, args.within_bottom)):
            raise InvalidSelectorError("All four region bounds are required together.")
        region = Bounds(args.within_left, args.within_top, args.within_right, args.within_bottom)
    return Selector(
        text=args.text,
        description=args.description,
        resource_id=args.resource_id,
        class_name=args.class_name,
        package_name=args.package_name,
        clickable=args.clickable,
        scrollable=args.scrollable,
        selected=args.selected,
        checked=args.checked,
        focusable=args.focusable,
        parent_text=args.parent_text,
        region=region,
        occurrence=args.occurrence,
        partial=args.partial,
    )


def wait_visible(adb: Adb, selector: Selector, timeout: float = 10.0) -> UiElement:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            return resolve(adb.elements(), selector)
        except ElementNotFoundError:
            time.sleep(0.25)
    raise ActionVerificationError(f"Timed out waiting for selector: {selector}")


def tap(adb: Adb, selector: Selector, wait_selector: Optional[Selector] = None, timeout: float = 10.0) -> None:
    item = resolve(adb.elements(), selector)
    adb.shell("input", "tap", str(item.bounds.center_x), str(item.bounds.center_y))
    if wait_selector:
        wait_visible(adb, wait_selector, timeout)


def swipe(adb: Adb, direction: str, amount: float, duration_ms: int, container: Optional[UiElement] = None) -> None:
    if not 0.1 <= amount <= 0.95:
        raise InvalidSelectorError("Swipe amount must be between 0.1 and 0.95.")
    bounds = container.bounds if container else Bounds(0, 0, adb.display().width, adb.display().height)
    if direction == "up":
        start, end = (bounds.center_x, int(bounds.bottom - bounds.height * 0.15)), (bounds.center_x, int(bounds.top + bounds.height * 0.15))
    elif direction == "down":
        start, end = (bounds.center_x, int(bounds.top + bounds.height * 0.15)), (bounds.center_x, int(bounds.bottom - bounds.height * 0.15))
    elif direction == "left":
        start, end = (int(bounds.right - bounds.width * 0.15), bounds.center_y), (int(bounds.left + bounds.width * 0.15), bounds.center_y)
    else:
        start, end = (int(bounds.left + bounds.width * 0.15), bounds.center_y), (int(bounds.right - bounds.width * 0.15), bounds.center_y)
    adb.shell("input", "swipe", str(start[0]), str(start[1]), str(end[0]), str(end[1]), str(max(50, min(duration_ms, 5000))))


def validate_routes(path: Path) -> dict:
    try:
        routes = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RouteValidationError(f"Could not read routes: {exc}") from exc
    if not isinstance(routes, dict):
        raise RouteValidationError("Routes root must be an object.")
    allowed = {"click", "click-text", "click-description", "scroll-to-text", "back"}
    for name, route in routes.items():
        if not isinstance(route, dict) or not isinstance(route.get("steps"), list):
            raise RouteValidationError(f"Route {name!r} needs a steps array.")
        if "verify" not in route:
            raise RouteValidationError(f"Route {name!r} needs final verification.")
        for index, step in enumerate(route["steps"], 1):
            if step.get("action") not in allowed:
                raise RouteValidationError(f"Route {name!r} step {index} has an unsupported action.")
            if step["action"] != "back" and not step.get("value") and step["action"] != "click":
                raise RouteValidationError(f"Route {name!r} step {index} needs a selector value.")
    return routes


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--serial")
    result.add_argument("--package", default=DEFAULT_PACKAGE)
    sub = result.add_subparsers(dest="command", required=True)
    sub.add_parser("status")
    sub.add_parser("current-activity")
    elements = sub.add_parser("elements")
    elements.add_argument("--all", action="store_true")
    elements.add_argument("--json", action="store_true")
    sub.add_parser("dump")
    screenshot = sub.add_parser("screenshot")
    screenshot.add_argument("--output")
    for command, attribute in (("click-text", "text"), ("click-description", "description"), ("click-id", "resource_id")):
        item = sub.add_parser(command)
        item.add_argument("value")
        item.add_argument("--partial", action="store_true")
        item.add_argument("--clickable", type=lambda value: value.lower() == "true")
        item.add_argument("--selected", type=lambda value: value.lower() == "true")
        item.add_argument("--parent-text")
        item.add_argument("--occurrence", type=int)
        item.add_argument("--wait-text")
        item.set_defaults(selector_field=attribute)
    scroll = sub.add_parser("scroll-to-text")
    scroll.add_argument("value")
    scroll.add_argument("--click", action="store_true")
    scroll.add_argument("--max-swipes", type=int, default=8)
    swipe_parser = sub.add_parser("swipe")
    swipe_parser.add_argument("direction", choices=("up", "down", "left", "right"))
    swipe_parser.add_argument("--amount", type=float, default=0.7)
    swipe_parser.add_argument("--duration-ms", type=int, default=450)
    input_parser = sub.add_parser("input")
    input_parser.add_argument("--text", required=True)
    input_parser.add_argument("--resource-id")
    input_parser.add_argument("--description")
    input_parser.add_argument("--clear", action="store_true")
    input_parser.add_argument("--verify", action="store_true")
    sub.add_parser("back")
    sub.add_parser("home")
    sub.add_parser("app-start")
    sub.add_parser("app-stop")
    assertion = sub.add_parser("assert-visible")
    assertion.add_argument("--text")
    assertion.add_argument("--description")
    assertion.add_argument("--resource-id")
    assertion.add_argument("--partial", action="store_true")
    routes = sub.add_parser("routes")
    routes.add_argument("action", choices=("validate", "list"))
    route = sub.add_parser("route")
    route.add_argument("name")
    route.add_argument("--dry-run", action="store_true")
    return result


def main(argv: Optional[list[str]] = None) -> int:
    args = parser().parse_args(argv)
    route_file = ROOT / "tools" / "routes.json"
    try:
        if args.command == "routes":
            routes = validate_routes(route_file)
            if args.action == "list":
                print("\n".join(sorted(routes)))
            else:
                print(f"validated {len(routes)} routes")
            return 0
        adb = Adb(args.serial)
        if args.command == "status":
            print(json.dumps({"serial": adb.ensure_target(), "package": args.package}, indent=2))
        elif args.command == "current-activity":
            adb.ensure_target()
            dump = adb.shell("dumpsys", "activity", "activities", timeout=20)
            match = re.search(r"(?:mResumedActivity:|mActivityComponent=).*?([\w.]+/[\w.$]+)", dump)
            if not match:
                raise AndroidUiError("Could not determine foreground activity.")
            print(match.group(1))
        elif args.command == "elements":
            values = [item.as_dict() for item in adb.elements(args.all)]
            print(json.dumps(values, ensure_ascii=False, indent=2) if args.json else "\n".join(f"[{item['index']}] {item['text'] or item['description'] or item['resource_id']} bounds={item['bounds']}" for item in values))
        elif args.command == "dump":
            print(adb.hierarchy())
        elif args.command == "screenshot":
            output = Path(args.output) if args.output else ROOT / "artifacts" / "android" / "screenshots" / f"screen-{time.strftime('%Y%m%d-%H%M%S')}.png"
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(adb.run("exec-out", "screencap", "-p", binary=True))
            print(output)
        elif args.command in ("click-text", "click-description", "click-id"):
            field = {"click-text": "text", "click-description": "description", "click-id": "resource_id"}[args.command]
            selector = Selector(**{field: args.value}, clickable=args.clickable, selected=args.selected, parent_text=args.parent_text, occurrence=args.occurrence, partial=args.partial)
            tap(adb, selector, Selector(text=args.wait_text) if args.wait_text else None)
        elif args.command == "scroll-to-text":
            for _ in range(args.max_swipes + 1):
                try:
                    item = resolve(adb.elements(), Selector(text=args.value))
                    if args.click:
                        tap(adb, Selector(text=args.value))
                    break
                except ElementNotFoundError:
                    containers = [item for item in adb.elements() if item.scrollable]
                    if not containers:
                        raise ElementNotFoundError(f"No scrollable container while searching for {args.value!r}.")
                    if len(containers) > 1:
                        containers.sort(key=lambda item: item.bounds.area, reverse=True)
                    swipe(adb, "up", 0.7, 450, containers[0])
            else:
                raise ElementNotFoundError(f"Target {args.value!r} was not found after bounded swipes.")
        elif args.command == "swipe":
            swipe(adb, args.direction, args.amount, args.duration_ms)
        elif args.command == "input":
            if not args.resource_id and not args.description:
                raise InvalidSelectorError("Input requires --resource-id or --description for safe field targeting.")
            field = Selector(resource_id=args.resource_id, description=args.description, focusable=True)
            target = resolve(adb.elements(), field)
            tap(adb, Selector(resource_id=target.resource_id) if target.resource_id else Selector(description=target.description))
            if args.clear:
                adb.shell("input", "keyevent", "KEYCODE_CTRL_LEFT")
                adb.shell("input", "keyevent", "KEYCODE_A")
                adb.shell("input", "keyevent", "KEYCODE_DEL")
            adb.shell("input", "text", args.text.replace(" ", "%s"))
            if args.verify:
                wait_visible(adb, Selector(text=args.text))
        elif args.command == "back":
            adb.shell("input", "keyevent", "4")
        elif args.command == "home":
            adb.shell("input", "keyevent", "3")
        elif args.command == "app-start":
            adb.ensure_target()
            adb.shell("monkey", "-p", args.package, "1")
        elif args.command == "app-stop":
            adb.ensure_target()
            adb.shell("am", "force-stop", args.package)
        elif args.command == "assert-visible":
            selector = Selector(text=args.text, description=args.description, resource_id=args.resource_id, partial=args.partial)
            resolve(adb.elements(), selector)
            print("ok")
        elif args.command == "route":
            routes = validate_routes(route_file)
            if args.name not in routes:
                raise RouteValidationError(f"Unknown route: {args.name}")
            if args.dry_run:
                print(f"validated route {args.name}")
            else:
                raise RouteExecutionError("Route execution requires the hardened route schema migration before use.")
        return 0
    except AndroidUiError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
