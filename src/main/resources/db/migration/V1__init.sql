CREATE TABLE site (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    verification_mode VARCHAR(20)  NOT NULL DEFAULT 'FACE_PLATE'
        CHECK (verification_mode IN ('FACE_PLATE', 'PLATE_ONLY', 'PLATE_RFID'))
);

CREATE TABLE gate (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(64) NOT NULL UNIQUE,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('ENTRY', 'EXIT')),
    site_id   BIGINT      NOT NULL REFERENCES site (id)
);

CREATE TABLE vehicle (
    id           BIGSERIAL PRIMARY KEY,
    plate_number VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE access_session (
    id                    BIGSERIAL PRIMARY KEY,
    vehicle_id            BIGINT      NOT NULL REFERENCES vehicle (id),
    entry_gate_id         BIGINT      NOT NULL REFERENCES gate (id),
    exit_gate_id          BIGINT REFERENCES gate (id),
    entry_face_profile_id VARCHAR(64),
    entered_at            TIMESTAMPTZ NOT NULL,
    exited_at             TIMESTAMPTZ,
    version               BIGINT      NOT NULL DEFAULT 0,
    CHECK (exited_at IS NULL OR exited_at >= entered_at)
);

CREATE UNIQUE INDEX ux_access_session_open_per_vehicle
    ON access_session (vehicle_id) WHERE exited_at IS NULL;

CREATE INDEX ix_access_session_entry_gate_open
    ON access_session (entry_gate_id) WHERE exited_at IS NULL;
