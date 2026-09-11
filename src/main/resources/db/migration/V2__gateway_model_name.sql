-- Gateway-unique model identity.
--
-- Before this migration a tenant could hold at most two rows per model_name
-- (one primary, one fallback), which made it impossible to register the same
-- underlying model twice with different credentials, endpoints or rate limits.
--
-- Each config now carries a service-wide unique name of the form
--   ai-bridge-<sequence>-<slugified model name>
-- where <sequence> counts up per slug. Clients may send that name in the
-- OpenAI `model` field to pin a request to one specific config.

ALTER TABLE llm_config ADD COLUMN model_slug VARCHAR(200);
ALTER TABLE llm_config ADD COLUMN model_sequence INTEGER;
ALTER TABLE llm_config ADD COLUMN gateway_model_name VARCHAR(300);

-- Backfill. The sequence partitions on the slug rather than the raw model name
-- so that two names that normalise to the same slug cannot collide.
WITH numbered AS (
    SELECT id,
           COALESCE(
               NULLIF(trim(BOTH '-' FROM regexp_replace(lower(model_name), '[^a-z0-9]+', '-', 'g')), ''),
               'model'
           ) AS slug,
           ROW_NUMBER() OVER (
               PARTITION BY COALESCE(
                   NULLIF(trim(BOTH '-' FROM regexp_replace(lower(model_name), '[^a-z0-9]+', '-', 'g')), ''),
                   'model'
               )
               ORDER BY created_at, id
           ) AS seq
    FROM llm_config
)
UPDATE llm_config c
SET model_slug = n.slug,
    model_sequence = n.seq,
    gateway_model_name = 'ai-bridge-' || n.seq || '-' || n.slug
FROM numbered n
WHERE c.id = n.id;

ALTER TABLE llm_config ALTER COLUMN model_slug SET NOT NULL;
ALTER TABLE llm_config ALTER COLUMN model_sequence SET NOT NULL;
ALTER TABLE llm_config ALTER COLUMN gateway_model_name SET NOT NULL;

-- The old constraint is what blocked registering the same model twice.
DROP INDEX IF EXISTS uq_llm_config_tenant_model_fallback;

-- Identity is now the gateway model name, which is unique across the service.
CREATE UNIQUE INDEX uq_llm_config_gateway_model
    ON llm_config (gateway_model_name);

-- Backstop for the sequence allocator: two concurrent inserts cannot claim the
-- same number for one slug even if the advisory lock is bypassed.
CREATE UNIQUE INDEX uq_llm_config_slug_sequence
    ON llm_config (model_slug, model_sequence);
