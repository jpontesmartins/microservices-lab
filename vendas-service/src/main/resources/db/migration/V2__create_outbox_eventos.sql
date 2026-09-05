-- V2: Transactional Outbox Pattern - tabela de eventos pendentes de publicacao

CREATE TABLE public.outbox_eventos (
    id bigint NOT NULL,
    aggregate_id character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    payload text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    published boolean NOT NULL DEFAULT false
);

CREATE SEQUENCE public.outbox_eventos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.outbox_eventos_id_seq OWNED BY public.outbox_eventos.id;
ALTER TABLE ONLY public.outbox_eventos ALTER COLUMN id SET DEFAULT nextval('public.outbox_eventos_id_seq'::regclass);

ALTER TABLE ONLY public.outbox_eventos ADD CONSTRAINT outbox_eventos_pkey PRIMARY KEY (id);
CREATE INDEX idx_outbox_eventos_unpublished ON public.outbox_eventos (published, created_at) WHERE published = false;
