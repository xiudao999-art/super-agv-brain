DROP TABLE IF EXISTS lab_config_link;
DROP TABLE IF EXISTS lab_config_object;
DROP TABLE IF EXISTS lab_config;
DROP TABLE IF EXISTS location;

CREATE TABLE location (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_code VARCHAR(64) NOT NULL
);

CREATE TABLE lab_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    space_id VARCHAR(36) NOT NULL,
    space_code VARCHAR(64) NOT NULL,
    space_name VARCHAR(128) NOT NULL,
    map_name VARCHAR(128) NOT NULL,
    map_version VARCHAR(64) NOT NULL,
    map_file_ref VARCHAR(512) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lab_config_space_revision UNIQUE (space_id, revision)
);

CREATE TABLE lab_config_object (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    location_id BIGINT NULL,
    nav_object_id BIGINT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    type VARCHAR(64) NOT NULL,
    coordinate_frame VARCHAR(16) NOT NULL,
    x DECIMAL(12,4) NOT NULL,
    y DECIMAL(12,4) NOT NULL,
    z DECIMAL(12,4) NULL,
    rx DECIMAL(9,4) NULL,
    ry DECIMAL(9,4) NULL,
    rz DECIMAL(9,4) NOT NULL,
    CONSTRAINT fk_lab_object_config FOREIGN KEY (config_id) REFERENCES lab_config(id),
    CONSTRAINT fk_lab_object_parent FOREIGN KEY (parent_id) REFERENCES lab_config_object(id),
    CONSTRAINT fk_lab_object_nav FOREIGN KEY (nav_object_id) REFERENCES lab_config_object(id),
    CONSTRAINT fk_lab_object_location FOREIGN KEY (location_id) REFERENCES location(id),
    CONSTRAINT uk_lab_object_code UNIQUE (config_id, kind, code)
);

CREATE TABLE lab_config_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    start_object_id BIGINT NOT NULL,
    end_object_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    speed_limit DECIMAL(8,3) NOT NULL,
    CONSTRAINT fk_lab_link_config FOREIGN KEY (config_id) REFERENCES lab_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_link_start FOREIGN KEY (start_object_id) REFERENCES lab_config_object(id),
    CONSTRAINT fk_lab_link_end FOREIGN KEY (end_object_id) REFERENCES lab_config_object(id),
    CONSTRAINT uk_lab_link_code UNIQUE (config_id, code)
);
