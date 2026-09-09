package eu.kanade.domain

import exh.ocr.OcrIndexRepository
import exh.recs.discovery.GetNonInstalledSourceSuggestions
import exh.recs.evaluation.GetSourceEvaluationCandidates
import exh.recs.matching.ConfirmedGroupLocalTrackingPropagator
import exh.recs.matching.CrossSourceIdentityAuthorizationResolver
import tachiyomi.data.chapter.ChapterLinePreferenceRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.data.libraryUpdateErrorMessage.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.data.taste.AlternateSourceBridgeRepositoryImpl
import tachiyomi.data.taste.MangaSourceQualitySignalRepositoryImpl
import tachiyomi.data.taste.RecommendationCacheRepositoryImpl
import tachiyomi.data.taste.RecommendationCandidateMemoryRepositoryImpl
import tachiyomi.data.taste.RecommendationDiscoveryProgressRepositoryImpl
import tachiyomi.data.taste.SourceEvaluationRepositoryImpl
import tachiyomi.data.taste.SourceEvaluationSafetyRepositoryImpl
import tachiyomi.data.taste.SourceRecommendationFitRepositoryImpl
import tachiyomi.data.taste.TasteRepositoryImpl
import tachiyomi.data.taste.UnsafeExtensionPackageRepositoryImpl
import tachiyomi.data.tracker.LocalTrackerRepositoryImpl
import tachiyomi.domain.chapter.repository.ChapterLinePreferenceRepository
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
import tachiyomi.domain.taste.interactor.ClearAlternateSourceBridges
import tachiyomi.domain.taste.interactor.ClearCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.ClearCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ClearMangaTaste
import tachiyomi.domain.taste.interactor.ClearRecommendationCache
import tachiyomi.domain.taste.interactor.ClearRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.ClearRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.ClearSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.ClearSourceEvaluations
import tachiyomi.domain.taste.interactor.ClearTagTaste
import tachiyomi.domain.taste.interactor.ClearUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.DeleteCrossSourceMangaLink
import tachiyomi.domain.taste.interactor.DeleteMangaSourceQualitySignal
import tachiyomi.domain.taste.interactor.DeleteRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.DeleteSourceEvaluation
import tachiyomi.domain.taste.interactor.DeleteSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.DeleteUnsafeExtensionPackage
import tachiyomi.domain.taste.interactor.GetAlternateSourceBridge
import tachiyomi.domain.taste.interactor.GetChapterCountsByMangaIds
import tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.GetCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.GetCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.GetDisabledRecommendationSources
import tachiyomi.domain.taste.interactor.GetKnownRecommendationMangaIds
import tachiyomi.domain.taste.interactor.GetMangaSourceQualitySignals
import tachiyomi.domain.taste.interactor.GetMangaTaste
import tachiyomi.domain.taste.interactor.GetRecommendationCache
import tachiyomi.domain.taste.interactor.GetRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.GetRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.GetSourceEvaluation
import tachiyomi.domain.taste.interactor.GetSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.GetSourceEvaluationUnsafeSources
import tachiyomi.domain.taste.interactor.GetSourceEvaluations
import tachiyomi.domain.taste.interactor.GetSourceRecommendationFit
import tachiyomi.domain.taste.interactor.GetTagAliases
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.GetTasteDiagnostics
import tachiyomi.domain.taste.interactor.GetTasteProfile
import tachiyomi.domain.taste.interactor.GetTasteSuggestions
import tachiyomi.domain.taste.interactor.GetUnsafeExtensionPackages
import tachiyomi.domain.taste.interactor.MarkSourceEvaluationUnsafe
import tachiyomi.domain.taste.interactor.PruneRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.ReplaceAlternateSourceBridge
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecision
import tachiyomi.domain.taste.interactor.ReplaceCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.ReplaceSourceEvaluation
import tachiyomi.domain.taste.interactor.SetCrossSourceGroupPrimary
import tachiyomi.domain.taste.interactor.SetMangaTaste
import tachiyomi.domain.taste.interactor.SetMangaTasteBatch
import tachiyomi.domain.taste.interactor.SetRecommendationSourceEnabled
import tachiyomi.domain.taste.interactor.SetTagTaste
import tachiyomi.domain.taste.interactor.UpsertAlternateSourceBridge
import tachiyomi.domain.taste.interactor.UpsertCrossSourceIdentityDecisions
import tachiyomi.domain.taste.interactor.UpsertCrossSourceMangaLinks
import tachiyomi.domain.taste.interactor.UpsertMangaSourceQualitySignal
import tachiyomi.domain.taste.interactor.UpsertRecommendationCache
import tachiyomi.domain.taste.interactor.UpsertRecommendationCandidateMemory
import tachiyomi.domain.taste.interactor.UpsertRecommendationDiscoveryProgress
import tachiyomi.domain.taste.interactor.UpsertSourceEvaluation
import tachiyomi.domain.taste.interactor.UpsertSourceEvaluationProbeMarker
import tachiyomi.domain.taste.interactor.UpsertSourceRecommendationFit
import tachiyomi.domain.taste.interactor.UpsertTagAlias
import tachiyomi.domain.taste.interactor.UpsertUnsafeExtensionPackage
import tachiyomi.domain.taste.repository.AlternateSourceBridgeRepository
import tachiyomi.domain.taste.repository.MangaSourceQualitySignalRepository
import tachiyomi.domain.taste.repository.RecommendationCacheRepository
import tachiyomi.domain.taste.repository.RecommendationCandidateMemoryRepository
import tachiyomi.domain.taste.repository.RecommendationDiscoveryProgressRepository
import tachiyomi.domain.taste.repository.SourceEvaluationRepository
import tachiyomi.domain.taste.repository.SourceEvaluationSafetyRepository
import tachiyomi.domain.taste.repository.SourceRecommendationFitRepository
import tachiyomi.domain.taste.repository.TasteRepository
import tachiyomi.domain.taste.repository.UnsafeExtensionPackageRepository
import tachiyomi.domain.tracker.repository.LocalTrackerRepository
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
        addSingletonFactory<LocalTrackerRepository> { LocalTrackerRepositoryImpl(get()) }
        addSingletonFactory<ChapterLinePreferenceRepository> { ChapterLinePreferenceRepositoryImpl(get()) }
        addFactory { GetMangaTaste(get()) }
        addFactory { SetMangaTaste(get()) }
        addFactory { SetMangaTasteBatch(get()) }
        addFactory { ClearMangaTaste(get()) }
        addFactory {
            exh.recs.matching.ConfirmedTrackedMangaTasteTargets(get(), get(), get(), get())
        }
        addFactory {
            exh.recs.matching.ConfirmedMangaGroupTargets(get(), get(), get())
        }
        addFactory { ConfirmedGroupLocalTrackingPropagator(get()) }
        addFactory { GetTasteProfile(get(), get()) }
        // KMK v0.8.10: Taste Suggestions -- deliberately a separate read-only interactor from
        // GetTasteProfile above (which feeds live recommendation/source-evaluation scoring and is
        // not touched by this addition).
        addFactory { GetTasteSuggestions(get(), get()) }
        // KMK v0.8.10: Diagnostics -- read-only, reuses GetTasteProfile (via Injekt.get() above)
        // for the confidence signal instead of recomputing it a second way.
        addFactory { GetTasteDiagnostics(get(), get(), get()) }
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
        // KMK v0.8.20: atomic ungroup + typed transactional restore for the group-action Undo Journal
        addFactory { tachiyomi.domain.taste.interactor.DeleteCrossSourceGroupCompletely(get()) }
        addFactory { tachiyomi.domain.taste.interactor.RestoreCrossSourceGroupState(get()) }
        // KMK <--
        // KMK --> v0.8.1-fix2: user-selected primary version per confirmed link group (v0.8.0).
        // These were introduced in v0.8.0 but never registered here, causing an Injekt
        // InjektionException crash ("No registered instance or factory for type class
        // tachiyomi.domain.taste.interactor.GetCrossSourceGroupPrimary") whenever
        // LovedMangaScreenModel, LinkedVersionListScreenModel, TasteBackupCreator, or
        // TasteRestorer requested any of these three interactors.
        addFactory { GetCrossSourceGroupPrimary(get()) }
        addFactory { SetCrossSourceGroupPrimary(get()) }
        addFactory { ClearCrossSourceGroupPrimary(get()) }
        addFactory { GetCrossSourceIdentityDecisions(get()) }
        addFactory { UpsertCrossSourceIdentityDecisions(get()) }
        addFactory { ReplaceCrossSourceIdentityDecision(get()) }
        addFactory { ReplaceCrossSourceIdentityDecisions(get()) }
        addFactory { ClearCrossSourceIdentityDecisions(get()) }
        addFactory { CrossSourceIdentityAuthorizationResolver(get()) }
        addSingletonFactory<AlternateSourceBridgeRepository> { AlternateSourceBridgeRepositoryImpl(get()) }
        addFactory { GetAlternateSourceBridge(get()) }
        addFactory { UpsertAlternateSourceBridge(get()) }
        addFactory { ReplaceAlternateSourceBridge(get()) }
        addFactory { ClearAlternateSourceBridges(get()) }
        // KMK <--
        // KMK --> v0.7.8: user-confirmed source quality signals
        addSingletonFactory<MangaSourceQualitySignalRepository> { MangaSourceQualitySignalRepositoryImpl(get()) }
        addFactory { GetMangaSourceQualitySignals(get()) }
        addFactory { UpsertMangaSourceQualitySignal(get()) }
        // KMK --> v0.7.27
        addFactory { DeleteMangaSourceQualitySignal(get()) }
        // KMK <--
        // KMK <--

        addSingletonFactory<RecommendationCacheRepository> { RecommendationCacheRepositoryImpl(get()) }
        addFactory { GetRecommendationCache(get()) }
        addFactory { UpsertRecommendationCache(get()) }
        addFactory { ClearRecommendationCache(get()) }
        // KMK --> v0.7.38: For You candidate discovery memory (local-only, not in backup/sync)
        addSingletonFactory<RecommendationCandidateMemoryRepository> { RecommendationCandidateMemoryRepositoryImpl(get()) }
        addFactory { GetRecommendationCandidateMemory(get()) }
        addFactory { UpsertRecommendationCandidateMemory(get()) }
        addFactory { DeleteRecommendationCandidateMemory(get()) }
        addFactory { PruneRecommendationCandidateMemory(get()) }
        addFactory { ClearRecommendationCandidateMemory(get()) }
        // KMK <--
        // KMK --> v0.7.39: For You rolling discovery progress (local-only, not in backup/sync)
        addSingletonFactory<RecommendationDiscoveryProgressRepository> { RecommendationDiscoveryProgressRepositoryImpl(get()) }
        addFactory { GetRecommendationDiscoveryProgress(get()) }
        addFactory { UpsertRecommendationDiscoveryProgress(get()) }
        addFactory { ClearRecommendationDiscoveryProgress(get()) }
        // KMK <--
        // local-only For You
        // exposure history (not in backup/sync/export -- see RecommendationExposureRepository KDoc)
        addSingletonFactory<tachiyomi.domain.taste.repository.RecommendationExposureRepository> {
            tachiyomi.data.taste.RecommendationExposureRepositoryImpl(get())
        }
        addFactory { tachiyomi.domain.taste.interactor.GetRecommendationExposure(get()) }
        addFactory { tachiyomi.domain.taste.interactor.RecordRecommendationExposure(get()) }
        addFactory { tachiyomi.domain.taste.interactor.PruneRecommendationExposure(get()) }
        addFactory { tachiyomi.domain.taste.interactor.ClearRecommendationExposure(get()) }
        // KMK <--

        addSingletonFactory<SourceEvaluationRepository> { SourceEvaluationRepositoryImpl(get()) }
        addFactory { GetSourceEvaluations(get()) }
        addFactory { GetSourceEvaluation(get()) }
        addFactory { UpsertSourceEvaluation(get()) }
        addFactory { DeleteSourceEvaluation(get()) }
        addFactory { ReplaceSourceEvaluation(get()) }
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
