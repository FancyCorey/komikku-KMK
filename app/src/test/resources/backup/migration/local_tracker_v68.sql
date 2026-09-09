INSERT INTO local_tracked_work(
    id, title, normalized_title, status, last_chapter_source,
    last_chapter_url, last_chapter_label, last_progress_at, created_at, updated_at
) VALUES (
    'fixture-work-68', 'Fixture Work', 'fixture work', 'READING', 101,
    '/fixture/chapter-12', 'Chapter 12', 1500, 1000, 2000
);

INSERT INTO local_tracked_work_source(
    work_id, source, url, title, confidence, confirmation, created_at, updated_at
) VALUES (
    'fixture-work-68', 101, '/fixture/manga', 'Fixture Work', 100,
    'USER_CONFIRMED', 1000, 2000
);

INSERT INTO local_tracked_work_list(
    work_id, list_name, normalized_name, created_at
) VALUES (
    'fixture-work-68', 'Reading', 'reading', 1100
);
