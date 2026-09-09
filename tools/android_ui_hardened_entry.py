"""Safe entry point for the hardened Android UI helper.

This module exists because the original helper is retained as a historical
proof-of-concept. It fixes the model normalization defect without changing
that preserved file in place.
"""

from __future__ import annotations

import sys

from tools import android_ui_hardened as implementation


def _deduplicate(elements):
    result = []
    seen = set()
    for element in elements:
        key = (
            element.text,
            element.description,
            element.resource_id,
            element.class_name,
            element.bounds.as_string(),
            element.clickable,
            element.selected,
        )
        if key in seen:
            continue
        seen.add(key)
        result.append(element)
    return [
        implementation.UiElement(
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


implementation.deduplicate = _deduplicate


def main(argv=None):
    return implementation.main(argv)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
