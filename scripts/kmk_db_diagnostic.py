import json
import sqlite3
from pathlib import Path


DB_PATH = Path("tachiyomi_kmk_debug_diagnostic.db")


def rows_to_dicts(cursor, rows):
    names = [d[0] for d in cursor.description]
    return [dict(zip(names, row)) for row in rows]


def main():
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()

    tables = [
        row[0]
        for row in cur.execute("select name from sqlite_master where type='table' order by name")
    ]
    interesting = [
        t
        for t in tables
        if any(key in t.lower() for key in ["source", "recommend", "taste", "manga", "ocr"])
    ]
    print("TABLES")
    print(json.dumps(interesting, indent=2))

    print("\nCOUNTS")
    for table in interesting:
        try:
            count = cur.execute(f"select count(*) from {table}").fetchone()[0]
        except Exception as e:
            count = f"ERR {e}"
        print(f"{table}: {count}")

    print("\nSCHEMA: source_evaluation")
    for row in cur.execute("pragma table_info(source_evaluation)"):
        print(row)

    print("\nSCHEMA: source_recommendation_fit")
    for row in cur.execute("pragma table_info(source_recommendation_fit)"):
        print(row)

    print("\nELFTOON SOURCE EVALUATION")
    q = """
        select *
        from source_evaluation
        where lower(extension_name) like '%elf%'
           or lower(source_name) like '%elf%'
           or lower(extension_pkg_name) like '%elf%'
        order by evaluated_at desc
        limit 20
    """
    cur.execute(q)
    print(json.dumps(rows_to_dicts(cur, cur.fetchall()), indent=2, default=str))

    print("\nELFTOON REC FIT")
    q = """
        select *
        from source_recommendation_fit
        where evaluation_key in (
            select evaluation_key
            from source_evaluation
            where lower(extension_name) like '%elf%'
               or lower(source_name) like '%elf%'
               or lower(extension_pkg_name) like '%elf%'
        )
        order by evaluated_at desc
        limit 20
    """
    cur.execute(q)
    print(json.dumps(rows_to_dicts(cur, cur.fetchall()), indent=2, default=str))

    print("\nTOP SOURCE EVALUATIONS BY VERDICT")
    q = """
        select source_name, extension_name, source_lang, sample_count, preferred_tag_match_count,
               blocked_tag_match_count, explicit_signal_count, ecchi_signal_count,
               quality_score, recommendation_fit_score, search_reliability_score,
               catalogue_metadata_confidence, verdict, evaluated_at
        from source_evaluation
        order by
            case verdict
                when 'STRONG_FIT' then 1
                when 'WORTH_TRYING' then 2
                when 'NEUTRAL' then 3
                when 'WEAK' then 4
                when 'ERROR' then 5
                else 9
            end,
            recommendation_fit_score desc,
            quality_score desc
        limit 80
    """
    cur.execute(q)
    print(json.dumps(rows_to_dicts(cur, cur.fetchall()), indent=2, default=str))


if __name__ == "__main__":
    main()
