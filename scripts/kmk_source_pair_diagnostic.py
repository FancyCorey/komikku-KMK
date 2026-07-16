import json
import sqlite3
from pathlib import Path


DB_PATH = Path("tachiyomi_kmk_debug_diagnostic.db")
SOURCE_PATTERNS = ["%elf%", "%cali%", "%kali%"]


def rows(cur, query, params=()):
    cur.execute(query, params)
    names = [d[0] for d in cur.description]
    return [dict(zip(names, row)) for row in cur.fetchall()]


def main():
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()

    print("SOURCE_EVALUATION_MATCHES")
    print(json.dumps(rows(cur, """
        select
            evaluation_key,
            source_id,
            extension_pkg_name,
            extension_name,
            source_name,
            lang,
            base_url,
            repo_name,
            sample_count,
            popular_count,
            latest_count,
            search_count,
            search_success_count,
            liked_title_match_count,
            preferred_tag_match_count,
            blocked_tag_match_count,
            explicit_signal_count,
            ecchi_signal_count,
            error_count,
            quality_score,
            recommendation_fit_score,
            search_reliability_score,
            explicit_score,
            ecchi_score,
            catalogue_metadata_confidence,
            verdict,
            sampled_titles_json,
            sampled_tags_json,
            error_message,
            evaluated_at
        from source_evaluation
        where lower(source_name) like ?
           or lower(extension_name) like ?
           or lower(extension_pkg_name) like ?
           or lower(source_name) like ?
           or lower(extension_name) like ?
           or lower(extension_pkg_name) like ?
           or lower(source_name) like ?
           or lower(extension_name) like ?
           or lower(extension_pkg_name) like ?
        order by lower(source_name), evaluated_at desc
    """, SOURCE_PATTERNS * 3), indent=2, default=str))

    print("\nSOURCE_RECOMMENDATION_FIT_MATCHES")
    print(json.dumps(rows(cur, """
        select
            srf.fit_key,
            srf.evaluation_key,
            srf.source_id,
            srf.extension_pkg_name,
            srf.extension_name,
            srf.source_name,
            srf.lang,
            srf.query_count,
            srf.query_success_count,
            srf.raw_result_count,
            srf.visible_candidate_count,
            srf.filtered_out_count,
            srf.blocked_tag_candidate_count,
            srf.matched_group_count,
            srf.top_picks_contribution,
            srf.no_matches_count,
            srf.error_count,
            srf.avg_candidate_score,
            srf.recommendation_quality_score,
            srf.verdict,
            srf.reasons_json,
            srf.error_message,
            srf.evaluated_at
        from source_recommendation_fit srf
        where srf.evaluation_key in (
            select evaluation_key
            from source_evaluation
            where lower(source_name) like ?
               or lower(extension_name) like ?
               or lower(extension_pkg_name) like ?
               or lower(source_name) like ?
               or lower(extension_name) like ?
               or lower(extension_pkg_name) like ?
               or lower(source_name) like ?
               or lower(extension_name) like ?
               or lower(extension_pkg_name) like ?
        )
        order by lower(srf.source_name), srf.evaluated_at desc
    """, SOURCE_PATTERNS * 3), indent=2, default=str))


if __name__ == "__main__":
    main()
