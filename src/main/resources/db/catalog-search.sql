-- Word search in the store catalogs (CatalogServiceImpl.search).
-- Runs on every start, after Hibernate has made the tables; once everything is there it changes nothing.

-- An index of the 3-letter pieces of every link's words, so a search for "g502" goes straight to the links
-- that have it instead of reading all of them
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_catalog_entries_search_text_trgm
    ON catalog_entries USING gin (search_text gin_trgm_ops);

-- The daily catalog update rewrites every link. With 30% of each page left free the new version of a link fits
-- in the same page and the index above is not touched; with full pages the update gets about 3 times slower.
-- Applies to pages written from now on; VACUUM FULL catalog_entries applies it to the existing ones.
ALTER TABLE catalog_entries SET (fillfactor = 70);
