# Security and integration behavior

KMK extends an app that interacts with source extensions, websites, trackers, Android packages, files, backups, and document providers. These boundaries are treated as untrusted inputs. Validation happens before navigation or mutation, and failures are reduced to stable categories before they reach user-facing diagnostics.

## External navigation

Deep links and embedded WebView navigation parse the complete URI and accept only the schemes and destinations owned by the route. Prefix lookalikes, local file access, JavaScript URLs, arbitrary intents, and malformed values are rejected.

## Source and extension calls

Source calls run through guarded runtime boundaries where the feature architecture supports them. A failure is attached to the source operation that produced it, while unrelated sources continue. Coroutine cancellation is rethrown rather than converted into an empty result or ordinary error.

## Mutations and history

Supported local mutations build a bounded receipt from the previous state before writing and commit it to Action History only after success. Undo checks the current state before restoring, so a later user change is not overwritten. Remote tracker writes and Android package operations are described separately because local state cannot guarantee reversal of an external effect.

## Storage and diagnostics

Exports use Android document APIs and retain the exact returned document reference for optional cleanup. Public diagnostics use fixed categories and bounded counts; they exclude raw URLs, paths, credentials, exception objects, source identities, and arbitrary remote messages.
