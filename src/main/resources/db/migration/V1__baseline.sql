-- V1__baseline.sql
-- Generated from the live schema Hibernate's ddl-auto produced, so this is exactly what already
-- exists rather than a hand-written approximation of it.
--
-- Existing databases are baselined at V1 and skip this file (spring.flyway.baseline-version=1).
-- A fresh database gets its whole schema from here - which is the point: the schema becomes a
-- reviewed artefact in git rather than a side effect of whatever the entity classes happened to
-- look like the last time the application started.


CREATE TABLE cart_items (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    added_at timestamp(6) with time zone NOT NULL,
    price_when_added numeric(12,2),
    quantity integer NOT NULL,
    sku character varying(60) NOT NULL,
    cart_id bigint NOT NULL
);

CREATE TABLE carts (
    id bigint NOT NULL,
    created_by character varying(100) NOT NULL,
    created_date timestamp(6) with time zone NOT NULL,
    updated_by character varying(100) NOT NULL,
    updated_date timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    last_activity_at timestamp(6) with time zone NOT NULL,
    user_public_id character varying(40) NOT NULL
);

CREATE SEQUENCE clickkart_order_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT cart_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY carts
    ADD CONSTRAINT carts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT uk_cart_items_cart_sku UNIQUE (cart_id, sku);

ALTER TABLE ONLY carts
    ADD CONSTRAINT uk_carts_user UNIQUE (user_public_id);

CREATE INDEX ix_cart_items_cart ON cart_items USING btree (cart_id);

CREATE INDEX ix_carts_last_activity ON carts USING btree (last_activity_at);

ALTER TABLE ONLY cart_items
    ADD CONSTRAINT fkpcttvuq4mxppo8sxggjtn5i2c FOREIGN KEY (cart_id) REFERENCES carts(id);

