# Technical reference

This directory contains two small XML references that help documentation tools and release checks inspect the same feature and screenshot information described in the public guides.

## Files

- [`feature-reference.xml`](feature-reference.xml) maps each KMK feature to its route, purpose, visible states, privacy rule, documentation page, and primary implementation owner.
- [`screenshot-manifest.xml`](screenshot-manifest.xml) records each reviewed public screenshot, the state it shows, its privacy checks, and its SHA-256 hash.

These files are not Android UI dumps and do not contain screen coordinates, device identifiers, account details, manga titles, source names, URLs, or local storage paths. Use the [user guide](../user-guide.md) for instructions, the [feature guides](../feature-guides/README.md) for behavior, and the [visual feature guide](../visual-guide/README.md) for the images themselves.

## Keeping the reference current

Update a feature entry when its route, visible states, privacy behavior, documentation page, or implementation owner changes. Update a screenshot entry when the image changes, then recompute its SHA-256 hash and repeat the privacy review. A source-only change does not require a new screenshot when the visible screen and behavior remain unchanged.
