-- V5: Tabela usuario e FK em pedidos

CREATE TABLE public.usuario (
    id bigserial NOT NULL,
    login varchar(255) NOT NULL UNIQUE,
    nome varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    CONSTRAINT usuario_pkey PRIMARY KEY (id)
);

ALTER TABLE public.pedidos
    ADD COLUMN usuario_id bigint,
    ADD CONSTRAINT fk_pedidos_usuario
        FOREIGN KEY (usuario_id) REFERENCES public.usuario(id);

INSERT INTO public.usuario (login, nome, email) VALUES
    ('admin', 'Administrador', 'admin@microservices.com'),
    ('user1', 'Usuario Teste', 'user1@microservices.com');
