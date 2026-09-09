# Security and integration behavior

KMK interacts with extensions, websites, trackers, Android packages, files, backups, and document providers. Data from those places is checked before the app opens a screen or changes saved information. Error messages use consistent categories instead of passing private or unpredictable details directly to the screen.

## External navigation

Deep links and embedded WebView navigation parse the complete URI and accept only the schemes and destinations owned by the route. Prefix lookalikes, local file access, JavaScript URLs, arbitrary intents, and malformed values are rejected.

## Source and extension calls

Where supported, source requests run through a shared safety layer. A failure stays with the source request that caused it while unrelated sources continue. Cancelling a task remains a cancellation instead of being shown as an empty result or ordinary error.

## Mutations and history

Before a supported local change, the app records the previous value. It adds that record to Action History only after the change succeeds. Undo checks the current value before restoring anything, so it cannot overwrite a newer change. Tracker updates and Android package actions are handled separately because the app cannot guarantee that an outside change can be reversed locally.

## Storage and diagnostics

Exports use Android's document APIs and remember the exact file they created so it can be removed later if requested. Public diagnostics use fixed categories and limited counts. They leave out raw URLs, paths, credentials, error objects, source names, and messages returned by outside services.
