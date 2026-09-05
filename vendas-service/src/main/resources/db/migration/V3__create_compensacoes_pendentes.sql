-- V3: Compensacao do Saga - tabela de cancelamentos pendentes com retry

CREATE TYPE compensacao_tipo AS ENUM ('ESTOQUE', 'FRETE');
CREATE TYPE compensacao_status AS ENUM ('PENDENTE', 'ENVIADO', 'FALHA');

CREATE TABLE public.compensacoes_pendentes (
    id              bigint NOT NULL,
    pedido_id       character varying(255) NOT NULL,
    tipo            compensacao_tipo NOT NULL,
    ref_id          character varying(255) NOT NULL,
    status          compensacao_status NOT NULL DEFAULT 'PENDENTE',
    attempts        integer NOT NULL DEFAULT 0,
    max_attempts    integer NOT NULL DEFAULT 3,
    last_error      text,
    created_at      timestamp(6) with time zone NOT NULL DEFAULT now(),
    updated_at      timestamp(6) with time zone NOT NULL DEFAULT now()
);

CREATE SEQUENCE public.compensacoes_pendentes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.compensacoes_pendentes_id_seq OWNED BY public.compensacoes_pendentes.id;
ALTER TABLE ONLY public.compensacoes_pendentes ALTER COLUMN id SET DEFAULT nextval('public.compensacoes_pendentes_id_seq'::regclass);

ALTER TABLE ONLY public.compensacoes_pendentes ADD CONSTRAINT compensacoes_pendentes_pkey PRIMARY KEY (id);
CREATE INDEX idx_compensacoes_pendentes_pending ON public.compensacoes_pendentes (status, created_at) WHERE status = 'PENDENTE';
