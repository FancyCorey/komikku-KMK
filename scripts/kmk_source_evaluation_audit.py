import json
import sqlite3
import sys
from pathlib import Path


DB_PATH = Path(sys.argv[1] if len(sys.argv) > 1 else "tachiyomi_kmk_debug_diagnostic_latest.db")


def rows(cur, query, params=()):
    cur.execute(query, params)
    names = [d[0] for d in cur.description]
    return [dict(zip(names, row)) for row in cur.fetchall()]


def scalar(cur, query, params=()):
    return cur.execute(query, params).fetchone()[0]


def section(name, value):
    print("\n" + name)
    print(json.dumps(value, indent=2, default=str))


def main():
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()

    section("DATABASE", {"path": str(DB_PATH), "exists": DB_PATH.exists(), "bytes": DB_PATH.stat().st_size})

    total = scalar(cur, "select count(*) from source_evaluation")
    non_error = scalar(cur, "select count(*) from source_evaluation where lower(verdict) != 'error'")
    missing_tags = scalar(cur, """
        select count(*) from source_evaluation
        where sampled_tags_json is null or sampled_tags_json = ''
    """)
    non_error_missing_tags = scalar(cur, """
        select count(*) from source_evaluation
        where lower(verdict) != 'error'
          and (sampled_tags_json is null or sampled_tags_json = '')
    """)
    with_tags = total - missing_tags
    non_error_with_tags = non_error - non_error_missing_tags
    section("TAG_COVERAGE_SUMMARY", {
        "total_source_evaluation_rows": total,
        "rows_with_sampled_tags": with_tags,
        "rows_missing_sampled_tags": missing_tags,
        "tagged_percent_all": round(with_tags / total * 100, 2) if total else 0,
        "non_error_rows": non_error,
        "non_error_rows_with_sampled_tags": non_error_with_tags,
        "non_error_rows_missing_sampled_tags": non_error_missing_tags,
        "tagged_percent_non_error": round(non_error_with_tags / non_error * 100, 2) if non_error else 0,
    })

    section("METADATA_PRESENCE_BY_VERDICT", rows(cur, """
        select
            verdict,
            count(*) as rows,
            sum(case when sampled_tags_json is null or sampled_tags_json = '' then 1 else 0 end) as missing_sampled_tags,
            round(100.0 * sum(case when sampled_tags_json is null or sampled_tags_json = '' then 1 else 0 end) / count(*), 1) as missing_tags_percent,
            round(avg(sample_count), 2) as avg_sample_count,
            round(avg(preferred_tag_match_count), 2) as avg_preferred_matches,
            round(avg(blocked_tag_match_count), 2) as avg_blocked_matches,
            round(avg(quality_score), 3) as avg_quality,
            round(avg(recommendation_fit_score), 3) as avg_fit
        from source_evaluation
        group by verdict
        order by verdict
    """))

    section("TAGGED_STRONG_OR_WORTH_TRYING_SAMPLES", rows(cur, """
        select
            source_name,
            extension_name,
            lang,
            sample_count,
            preferred_tag_match_count,
            blocked_tag_match_count,
            explicit_signal_count,
            ecchi_signal_count,
            quality_score,
            recommendation_fit_score,
            explicit_score,
            ecchi_score,
            catalogue_metadata_confidence,
            verdict,
            sampled_titles_json,
            sampled_tags_json
        from source_evaluation
        where lower(verdict) in ('strong_fit', 'worth_trying')
        order by recommendation_fit_score desc, preferred_tag_match_count desc
        limit 25
    """))

    section("UNTAGGED_HIGH_SAMPLE_WEAK_SAMPLES", rows(cur, """
        select
            source_name,
            extension_name,
            lang,
            sample_count,
            preferred_tag_match_count,
            blocked_tag_match_count,
            quality_score,
            recommendation_fit_score,
            catalogue_metadata_confidence,
            verdict,
            sampled_titles_json
        from source_evaluation
        where lower(verdict) = 'weak'
          and (sampled_tags_json is null or sampled_tags_json = '')
        order by sample_count desc, quality_score desc
        limit 25
    """))

    section("REC_FIT_DISTRIBUTION", rows(cur, """
        select
            verdict,
            count(*) as rows,
            round(avg(query_count), 2) as avg_query_count,
            round(avg(query_success_count), 2) as avg_query_success_count,
            round(avg(raw_result_count), 2) as avg_raw_result_count,
            round(avg(visible_candidate_count), 2) as avg_visible_candidate_count,
            round(avg(filtered_out_count), 2) as avg_filtered_out_count,
            round(avg(recommendation_quality_score), 3) as avg_quality_score
        from source_recommendation_fit
        group by verdict
        order by verdict
    """))

    section("TAG_TASTE", rows(cur, """
        select display_name, normalized_tag, preference, updated_at
        from tag_taste
        order by preference desc, lower(display_name)
    """))

    section("TAG_ALIAS_RELEVANT", rows(cur, """
        select alias, normalized_alias, group_key, display_name
        from tag_alias
        where lower(alias) in ('yaoi','yuri','shounen ai','shoujo ai','boys love','girls love','bl','gl','smut','adult','mature','ecchi')
           or lower(display_name) in ('yaoi','yuri','shounen ai','shoujo ai','boys love','girls love','bl','gl','smut','adult','mature','ecchi')
           or lower(group_key) in ('boys_love','girls_love','smut','adult','mature','ecchi')
        order by lower(group_key), lower(alias)
    """))


if __name__ == "__main__":
    main()
