-- V4: Converter colunas enum PostgreSQL para VARCHAR (compatibilidade com Hibernate JPQL)

DROP INDEX IF EXISTS public.idx_compensacoes_pendentes_pending;

ALTER TABLE public.compensacoes_pendentes ALTER COLUMN tipo TYPE VARCHAR(20);
ALTER TABLE public.compensacoes_pendentes ALTER COLUMN status TYPE VARCHAR(20);

DROP TYPE IF EXISTS compensacao_status CASCADE;
DROP TYPE IF EXISTS compensacao_tipo CASCADE;

CREATE INDEX idx_compensacoes_pendentes_pending ON public.compensacoes_pendentes (status, created_at) WHERE status = 'PENDENTE';
