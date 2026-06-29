package eu.kanade.domain

import exh.ocr.OcrIndexRepository
import exh.recs.discovery.GetNonInstalledSourceSuggestions
import exh.recs.evaluation.GetSourceEvaluationCandidates
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.data.libraryUpdateErrorMessage.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.data.taste.MangaSourceQualitySignalRepositoryImpl
import tachiyomi.data.taste.RecommendationCacheRepositoryImpl
import tachiyomi.data.taste.SourceEvaluationRepositoryImpl
import tachiyomi.data.taste.SourceEvaluationSafetyRepositoryImpl
import tachiyomi.data.taste.SourceRecommendationFitRepositoryImpl
import tachiyomi.data.taste.TasteRepositoryImpl
import tachiyomi.data.taste.UnsafeExtensionPackageRepositoryImpl
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.DeleteLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.ClearRecommendationCache
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.ClearSourceEvaluations
import tachiyomi.domain.taste.interactor.ClearTagTaste
import tachiyomi.domain.taste.interactor.ClearUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.DeleteSourceEvaluation
import tachiyomi.domain.taste.interactor.DeleteSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.DeleteUnsafeExtensionPackage
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetRecommendationCache
import tachiyomi.domain.taste.interactor.GetSourceEvaluation
import tachiyomi.domain.taste.interactor.GetSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetSourceEvaluationUnsafeSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetSourceRecommendationFit
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.GetUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.MarkSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.SetRecommendationSourceEnabled
import tachiyomi.domain.taste.interactor.SetTagTaste
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.interactor.UpsertSourceEvaluation
import tachiyomi.domain.taste.interactor.UpsertSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.UpsertSourceRecommendationFit
import tachiyomi.domain.taste.interactor.UpsertTagAlias
import tachiyomi.domain.taste.interactor.UpsertUnsafeExtensionPackage
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository
import tachiyomi.domain.taste.repository.RecommendationCacheRepository
import tachiyomi.domain.taste.repository.SourceEvaluationRepository
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository
import tachiyomi.domain.taste.repository.SourceRecommendationFitRepository
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class KMKDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<LibraryUpdateErrorWithRelationsRepository> {
            LibraryUpdateErrorWithRelationsRepositoryImpl(get())
        }
        addFactory { GetLibraryUpdateErrorWithRelations(get()) }

        addSingletonFactory<LibraryUpdateErrorMessageRepository> { LibraryUpdateErrorMessageRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrorMessages(get()) }
        addFactory { DeleteLibraryUpdateErrorMessages(get()) }
        addFactory { InsertLibraryUpdateErrorMessages(get()) }

        addSingletonFactory<LibraryUpdateErrorRepository> { LibraryUpdateErrorRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrors(get()) }
        addFactory { DeleteLibraryUpdateErrors(get()) }
        addFactory { InsertLibraryUpdateErrors(get()) }

        // KMK --> Personal recommendations taste profile
        addSingletonFactory<TasteRepository> { TasteRepositoryImpl(get()) }
        addFactory { GetMangaTaste(get()) }
        addFactory { SetMangaTaste(get()) }
        addFactory { SetMangaTasteBatch(get()) }
        addFactory { ClearMangaTaste(get()) }
        addFactory { GetTasteProfile(get(), get()) }
        addFactory { GetTagTaste(get()) }
        addFactory { SetTagTaste(get()) }
        addFactory { ClearTagTaste(get()) }
        addFactory { GetTagAliases(get()) }
        addFactory { UpsertTagAlias(get()) }
        addFactory { GetDisabledRecommendationSources(get()) }
        addFactory { SetRecommendationSourceEnabled(get()) }
        addFactory { GetKnownRecommendationMangaIds(get()) }
        // KMK --> v0.7.26: batch chapter counts for minimum-chapter filter
        addFactory { GetChapterCountsByMangaIds(get()) }
        // KMK <--
        // KMK --> v0.7.0: Phase 4 – cross-source link groups
        addFactory { GetCrossSourceMangaLinks(get()) }
        addFactory { UpsertCrossSourceMangaLinks(get()) }
        addFactory { DeleteCrossSourceMangaLink(get()) }
        // KMK <--
        // KMK --> v0.7.8: user-confirmed source quality signals
        addSingletonFactory<MangaSourceQualitySignalRepository> { MangaSourceQualitySignalRepositoryImpl(get()) }
        addFactory { GetMangaSourceQualitySignals(get()) }
        addFactory { UpsertMangaSourceQualitySignal(get()) }
        // KMK <--

        addSingletonFactory<RecommendationCacheRepository> { RecommendationCacheRepositoryImpl(get()) }
        addFactory { GetRecommendationCache(get()) }
        addFactory { UpsertRecommendationCache(get()) }
        addFactory { ClearRecommendationCache(get()) }

        addSingletonFactory<SourceEvaluationRepository> { SourceEvaluationRepositoryImpl(get()) }
        addFactory { GetSourceEvaluations(get()) }
        addFactory { GetSourceEvaluation(get()) }
        addFactory { UpsertSourceEvaluation(get()) }
        addFactory { DeleteSourceEvaluation(get()) }
        addFactory { ClearSourceEvaluations(get()) }
        addFactory { GetNonInstalledSourceSuggestions(get(), get(), get()) }
        // KMK --> v0.7.6: bounded recommendation-quality probe persistence
        addSingletonFactory<SourceRecommendationFitRepository> { SourceRecommendationFitRepositoryImpl(get()) }
        addFactory { GetSourceRecommendationFit(get()) }
        addFactory { UpsertSourceRecommendationFit(get()) }
        // KMK <--
        // KMK --> v0.6.16: Source Evaluation crash quarantine
        addSingletonFactory<SourceEvaluationSafetyRepository> { SourceEvaluationSafetyRepositoryImpl(get()) }
        addFactory { GetSourceEvaluationUnsafeSources(get()) }
        addFactory { GetSourceEvaluationProbeMarker(get()) }
        addFactory { UpsertSourceEvaluationProbeMarker(get()) }
        addFactory { ClearSourceEvaluationProbeMarker(get()) }
        addFactory { MarkSourceEvaluationUnsafe(get()) }
        addFactory { DeleteSourceEvaluationUnsafe(get()) }
        addFactory { ClearSourceEvaluationUnsafe(get()) }
        addFactory { GetSourceEvaluationCandidates(get(), get(), get(), get()) }
        // KMK --> OCR v0.1.0
        addSingletonFactory { OcrIndexRepository(get()) }
        // KMK <--
        // KMK --> v0.6.18: package-level extension load quarantine
        addSingletonFactory<UnsafeExtensionPackageRepository> { UnsafeExtensionPackageRepositoryImpl(get()) }
        addFactory { GetUnsafeExtensionPackages(get()) }
        addFactory { UpsertUnsafeExtensionPackage(get()) }
        addFactory { DeleteUnsafeExtensionPackage(get()) }
        addFactory { ClearUnsafeExtensionPackages(get()) }
        // KMK <--
    }
}
