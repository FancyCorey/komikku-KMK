import json
import sqlite3

DB='tachiyomi_kmk_debug_diagnostic.db'
con=sqlite3.connect(DB)
con.row_factory=sqlite3.Row
cur=con.cursor()

def dump(title, query, params=()):
    print('\n' + title)
    rows=[dict(r) for r in cur.execute(query, params)]
    print(json.dumps(rows, indent=2, default=str))

dump('SOURCE_EVALUATION_DISTRIBUTION', '''
select verdict, catalogue_metadata_confidence, count(*) as count,
       round(avg(sample_count), 2) as avg_samples,
       round(avg(preferred_tag_match_count), 2) as avg_preferred_matches,
       round(avg(blocked_tag_match_count), 2) as avg_blocked_matches,
       round(avg(recommendation_fit_score), 3) as avg_recommendation_fit,
       round(avg(quality_score), 3) as avg_quality
from source_evaluation
group by verdict, catalogue_metadata_confidence
order by verdict, catalogue_metadata_confidence
''')

dump('TOP_STRONG_OR_WORTH_TRYING', '''
select source_name, extension_name, lang, sample_count, preferred_tag_match_count,
       blocked_tag_match_count, quality_score, recommendation_fit_score,
       catalogue_metadata_confidence, verdict, sampled_titles_json, sampled_tags_json
from source_evaluation
where verdict in ('strong_fit','worth_trying','STRONG_FIT','WORTH_TRYING')
order by recommendation_fit_score desc, quality_score desc
limit 40
''')

dump('WEAK_WITH_GOOD_QUALITY_OR_SAMPLES', '''
select source_name, extension_name, lang, sample_count, preferred_tag_match_count,
       blocked_tag_match_count, quality_score, recommendation_fit_score,
       catalogue_metadata_confidence, verdict, sampled_titles_json, sampled_tags_json
from source_evaluation
where lower(verdict) = 'weak'
order by quality_score desc, sample_count desc, recommendation_fit_score desc
limit 60
''')

dump('REC_FIT_DISTRIBUTION', '''
select verdict, count(*) as count,
       round(avg(query_count),2) avg_queries,
       round(avg(query_success_count),2) avg_success,
       round(avg(raw_result_count),2) avg_raw,
       round(avg(visible_candidate_count),2) avg_visible,
       round(avg(filtered_out_count),2) avg_filtered,
       round(avg(avg_candidate_score),3) avg_candidate_score,
       round(avg(recommendation_quality_score),3) avg_quality_score
from source_recommendation_fit
group by verdict
order by verdict
''')

dump('ELFTOON_AND_NEIGHBORS', '''
select se.source_name, se.extension_name, se.lang, se.sample_count, se.preferred_tag_match_count,
       se.quality_score, se.recommendation_fit_score, se.catalogue_metadata_confidence, se.verdict,
       srf.query_count, srf.query_success_count, srf.raw_result_count, srf.visible_candidate_count,
       srf.filtered_out_count, srf.avg_candidate_score, srf.recommendation_quality_score,
       srf.verdict as rec_verdict, srf.reasons_json, srf.error_message,
       se.sampled_titles_json, se.sampled_tags_json
from source_evaluation se
left join source_recommendation_fit srf on srf.evaluation_key = se.evaluation_key
where lower(se.source_name) like '%elf%'
   or lower(se.extension_name) like '%elf%'
   or lower(se.extension_pkg_name) like '%elf%'
order by se.evaluated_at desc
''')

dump('TASTE_COUNTS', '''
select rating, count(*) as count
from manga_taste
group by rating
order by rating
''')

dump('TAG_TASTE', '''
select display_name, preference, updated_at
from tag_taste
order by preference desc, display_name
''')

dump('METADATA_PRESENCE_BY_VERDICT', '''
select verdict,
       count(*) as rows,
       sum(case when sampled_tags_json is null or sampled_tags_json = '' then 1 else 0 end) as missing_sampled_tags,
       round(100.0 * sum(case when sampled_tags_json is null or sampled_tags_json = '' then 1 else 0 end) / count(*), 1) as missing_tags_percent,
       round(avg(sample_count),2) as avg_sample_count,
       round(avg(preferred_tag_match_count),2) as avg_preferred_matches
from source_evaluation
group by verdict
order by verdict
''')
