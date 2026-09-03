-- V1: Schema inicial do estoque-service (gerado via Hibernate ddl-auto:create)

CREATE TABLE public.itens_estoque (
    sku character varying(255) NOT NULL,
    descricao character varying(255) NOT NULL,
    quantidade integer NOT NULL
);

CREATE TABLE public.reservas_estoque (
    id character varying(255) NOT NULL,
    pedido_id character varying(255) NOT NULL,
    quantidade integer NOT NULL,
    sku character varying(255) NOT NULL
);

ALTER TABLE ONLY public.itens_estoque ADD CONSTRAINT itens_estoque_pkey PRIMARY KEY (sku);
ALTER TABLE ONLY public.reservas_estoque ADD CONSTRAINT reservas_estoque_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.reservas_estoque ADD CONSTRAINT fki49ih55cd6pxymrn53dk6tqsk FOREIGN KEY (sku) REFERENCES public.itens_estoque(sku);

-- Seed data: itens iniciais de estoque
INSERT INTO public.itens_estoque (sku, descricao, quantidade) VALUES
    ('ABC-123', 'Teclado Mecanico', 42),
    ('XYZ-789', 'Mouse Gamer', 15),
    ('DEF-456', 'Monitor 27pol', 10),
    ('GHI-012', 'Webcam Full HD', 25),
    ('JKL-345', 'Headset Gamer', 30);
