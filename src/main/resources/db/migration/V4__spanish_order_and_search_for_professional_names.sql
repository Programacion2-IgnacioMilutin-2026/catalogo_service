-- Orden alfabético en español para el nombre y el apellido.
ALTER TABLE professionals ALTER COLUMN first_name TYPE VARCHAR COLLATE spanish_insensitive;
ALTER TABLE professionals ALTER COLUMN last_name TYPE VARCHAR COLLATE spanish_insensitive;

-- Búsqueda sin mayúsculas ni tildes, con la ñ igual a la n.
CREATE COLLATION search_insensitive (provider = icu, locale = 'und-u-ks-level1', deterministic = false);
