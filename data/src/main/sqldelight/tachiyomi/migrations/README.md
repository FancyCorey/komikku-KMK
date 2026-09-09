# SQLDelight Migrations

The integrated upstream baseline includes migration 46.

KMK additive migrations currently occupy 47 through 64. Before rebasing onto a newer upstream,
inspect newly added upstream migrations and renumber conflicting KMK migrations while preserving
their relative order, database registration, and migration-test coverage.
