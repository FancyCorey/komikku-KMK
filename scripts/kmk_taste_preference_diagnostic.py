import json
import sqlite3
from pathlib import Path


DB_PATH = Path("tachiyomi_kmk_debug_diagnostic.db")


def rows(cur, query, params=()):
    cur.execute(query, params)
    names = [d[0] for d in cur.description]
    return [dict(zip(names, row)) for row in cur.fetchall()]


def main():
    con = sqlite3.connect(DB_PATH)
    cur = con.cursor()

    print("TAG_TASTE")
    print(json.dumps(rows(cur, """
        select display_name, preference, updated_at
        from tag_taste
        order by preference desc, lower(display_name)
    """), indent=2, default=str))

    print("\nTAG_ALIAS")
    try:
        print(json.dumps(rows(cur, """
            select alias, group_key, display_name
            from tag_alias
            where lower(alias) in ('yaoi', 'yuri', 'boys love', 'girls love', 'smut', 'adult', 'shounen ai', 'shoujo ai')
               or lower(display_name) in ('yaoi', 'yuri', 'boys love', 'girls love', 'smut', 'adult', 'shounen ai', 'shoujo ai')
            order by lower(group_key), lower(alias)
        """), indent=2, default=str))
    except Exception as e:
        print(f"ERR {e}")


if __name__ == "__main__":
    main()
