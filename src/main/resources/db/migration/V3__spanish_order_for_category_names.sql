-- Orden alfabético en español.
CREATE COLLATION spanish_insensitive (provider = icu, locale = 'es-u-ks-level1', deterministic = false);

ALTER TABLE professional_categories ALTER COLUMN name TYPE VARCHAR COLLATE spanish_insensitive;
