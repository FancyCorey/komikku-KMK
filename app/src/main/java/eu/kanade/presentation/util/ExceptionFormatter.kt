package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.RecoverableSourceRuntimeException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.net.UnknownHostException

context(context: Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is HttpException -> return context.stringResource(MR.strings.exception_http, code)
            is UnknownHostException -> {
                return if (!context.isOnline()) {
                    context.stringResource(MR.strings.exception_offline)
                } else {
                    context.stringResource(KMR.strings.rec_error_network)
                }
            }

            is NoResultsException -> return context.stringResource(MR.strings.no_results_found)
            is SourceNotInstalledException -> return context.stringResource(MR.strings.loader_not_implemented_error)
            // KMK v0.8.10-fix2: a broken/incompletely-packaged extension (missing class, missing
            // method, incompatible class version, ...) throws a LinkageError, which previously fell
            // through to the generic "$className: $message" branch below and leaked a raw,
            // developer-facing message (e.g. "NoClassDefFoundError: Failed resolution of:
            // Lokhttp3/zstd/Zstd;") into normal per-source error rows. Sanitized, same as every
            // other classified case above.
            is LinkageError -> return context.stringResource(KMR.strings.rec_error_extension_incompatible)
            // KMK v0.8.12: RecoverableSourceRuntimeException (thrown by
            // getOrThrowSourceRuntimeException() for any recoverable per-source failure) carries the
            // real underlying failure as its `cause` -- without unwrapping here, every recoverable
            // failure surfaced through this path (including a LinkageError, which the branch above
            // would otherwise sanitize) fell through to the raw "$className: $message" branch below
            // as "RecoverableSourceRuntimeException: ...", leaking a developer-facing wrapper class
            // name into normal For You / recommendation row UI. Recurse on the real cause instead.
            is RecoverableSourceRuntimeException -> return sourceRuntimeFailure.formattedMessage
            // KMK v0.8.12: a source touched an uninitialized lateinit property (e.g. partially
            // constructed after an install/upgrade race) -- recoverable per-source, not a bug the
            // user can act on beyond retrying, so it gets the same generic non-fatal message as any
            // other unclassified internal failure rather than its raw class name.
            is UninitializedPropertyAccessException -> return context.stringResource(KMR.strings.rec_error_internal)
        }
        return context.stringResource(KMR.strings.rec_error_internal)
    }
