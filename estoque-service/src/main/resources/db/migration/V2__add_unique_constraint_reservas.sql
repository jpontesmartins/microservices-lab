-- V2: Adicionar constraint UNIQUE(pedido_id,sku) em reservas_estoque para idempotência

ALTER TABLE public.reservas_estoque
    ADD CONSTRAINT uk_reserva_pedido_sku UNIQUE (pedido_id, sku);
