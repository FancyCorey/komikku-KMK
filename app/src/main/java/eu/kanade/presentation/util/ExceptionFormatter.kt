package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
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
                    context.stringResource(MR.strings.exception_unknown_host, message ?: "")
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
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
