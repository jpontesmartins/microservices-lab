-- V1: Schema inicial do vendas-service (gerado via Hibernate ddl-auto:create)

CREATE TABLE public.pedidos (
    pedido_id character varying(255) NOT NULL,
    cep_destino character varying(255) NOT NULL,
    criado_em timestamp(6) with time zone NOT NULL,
    mensagem_erro character varying(255),
    status character varying(255) NOT NULL,
    transacao_id character varying(255),
    CONSTRAINT pedidos_status_check CHECK (((status)::text = ANY ((ARRAY['CRIADO'::character varying, 'ESTOQUE_RESERVADO'::character varying, 'FRETE_CALCULADO'::character varying, 'PAGO'::character varying, 'FALHA_ESTOQUE'::character varying, 'FALHA_FRETE'::character varying, 'FALHA_PAGAMENTO'::character varying, 'FALHA_TRANSITORIA'::character varying])::text[])))
);

CREATE TABLE public.pedido_itens (
    id bigint NOT NULL,
    frete_id character varying(255),
    prazo_entrega character varying(255),
    quantidade integer NOT NULL,
    reserva_id character varying(255),
    sku character varying(255) NOT NULL,
    valor_frete double precision,
    valor_unitario double precision NOT NULL,
    pedido_id character varying(255) NOT NULL
);

CREATE SEQUENCE public.pedido_itens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.pedido_itens_id_seq OWNED BY public.pedido_itens.id;
ALTER TABLE ONLY public.pedido_itens ALTER COLUMN id SET DEFAULT nextval('public.pedido_itens_id_seq'::regclass);

ALTER TABLE ONLY public.pedidos ADD CONSTRAINT pedidos_pkey PRIMARY KEY (pedido_id);
ALTER TABLE ONLY public.pedido_itens ADD CONSTRAINT pedido_itens_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.pedido_itens ADD CONSTRAINT fka3dnlbbof21fsp6gnngyqix1e FOREIGN KEY (pedido_id) REFERENCES public.pedidos(pedido_id);
