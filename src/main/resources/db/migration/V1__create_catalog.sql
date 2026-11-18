CREATE TABLE professional_categories (
    id          BIGINT  PRIMARY KEY,
    name        VARCHAR NOT NULL,
    description VARCHAR,
    enabled     BOOLEAN NOT NULL
);

CREATE TABLE professionals (
    id          BIGINT  PRIMARY KEY,
    category_id BIGINT  NOT NULL REFERENCES professional_categories (id),
    first_name  VARCHAR NOT NULL,
    last_name   VARCHAR NOT NULL,
    enabled     BOOLEAN NOT NULL
);

CREATE TABLE weekly_schedules (
    id                    BIGINT     PRIMARY KEY,
    professional_id       BIGINT     NOT NULL REFERENCES professionals (id),
    day_of_week           VARCHAR(9) NOT NULL,
    start_time            TIME       NOT NULL,
    end_time              TIME       NOT NULL,
    slot_duration_minutes INTEGER    NOT NULL,
    enabled               BOOLEAN    NOT NULL
);

CREATE TABLE catalog_sync_state (
    id                      INTEGER     PRIMARY KEY CHECK (id = 1),
    local_version           BIGINT,
    last_sync_at            TIMESTAMPTZ,
    last_successful_sync_at TIMESTAMPTZ,
    last_sync_type          VARCHAR(20),
    last_sync_result        VARCHAR(20),
    last_error_type         VARCHAR(30)
);

INSERT INTO catalog_sync_state (id) VALUES (1);
