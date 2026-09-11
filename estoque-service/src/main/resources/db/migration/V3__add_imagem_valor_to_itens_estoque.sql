-- V3: Adicionar colunas imagem e valor em itens_estoque

ALTER TABLE public.itens_estoque
    ADD COLUMN imagem VARCHAR(500),
    ADD COLUMN valor NUMERIC(10,2) NOT NULL DEFAULT 0.00;

-- Seed data: valores e imagens dos itens
UPDATE public.itens_estoque SET imagem = 'https://example.com/teclado.jpg', valor = 250.00 WHERE sku = 'ABC-123';
UPDATE public.itens_estoque SET imagem = 'https://example.com/mouse.jpg', valor = 150.00 WHERE sku = 'XYZ-789';
UPDATE public.itens_estoque SET imagem = 'https://example.com/monitor.jpg', valor = 1200.00 WHERE sku = 'DEF-456';
UPDATE public.itens_estoque SET imagem = 'https://example.com/webcam.jpg', valor = 200.00 WHERE sku = 'GHI-012';
UPDATE public.itens_estoque SET imagem = 'https://example.com/headset.jpg', valor = 300.00 WHERE sku = 'JKL-345';
