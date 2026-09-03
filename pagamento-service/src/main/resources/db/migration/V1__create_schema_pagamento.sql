-- V1: Schema inicial do pagamento-service

CREATE TABLE public.transacoes (
    id character varying(255) NOT NULL,
    pedido_id character varying(255) NOT NULL,
    valor double precision NOT NULL,
    status character varying(255) NOT NULL,
    criado_em timestamp(6) with time zone NOT NULL,
    CONSTRAINT transacoes_pkey PRIMARY KEY (id),
    CONSTRAINT uk_transacao_pedido UNIQUE (pedido_id)
);
