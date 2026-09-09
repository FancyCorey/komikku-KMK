# Security policy

## Supported source

Security fixes are applied to the current `main` branch and to releases identified as supported on the repository's Releases page. Older builds may contain known defects and should not be treated as supported merely because an APK remains downloadable.

## Report a vulnerability

Use [GitHub's private vulnerability reporting flow](https://github.com/FancyCorey/komikku-KMK/security/advisories/new) when it is available. Include the affected version or commit, the reachable behavior, impact, and the smallest safe reproduction. Do not attach credentials, private manga content, account data, raw backups, unrestricted logs, or device databases.

If private vulnerability reporting is unavailable, open a minimal issue asking the maintainer to enable a private channel. Do not publish exploit details or user data in the issue.

Ordinary bugs, feature requests, and source-extension failures belong in the normal issue tracker. Vulnerabilities inherited from official Komikku should also be disclosed to the upstream project through its preferred private process.

## Security boundaries

Installed extensions and configured trackers can communicate with their own services. Komikku KMK cannot guarantee the behavior of third-party extensions or services. Reports should distinguish a KMK defect from an extension, source website, Android document provider, or remote tracker issue.
