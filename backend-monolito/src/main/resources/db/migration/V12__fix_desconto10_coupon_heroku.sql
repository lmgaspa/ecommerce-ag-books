-- Garantir que o cupom DESCONTO10 exista e esteja consistente
-- Hotfix que já foi aplicado no Heroku e está marcado como versão 12 no Flyway do banco atual.

INSERT INTO coupons (
    code,
    name,
    description,
    discount_type,
    discount_value,
    minimum_order_value,
    maximum_discount_value,
    usage_limit,
    usage_limit_per_user,
    valid_from,
    valid_until,
    active,
    created_at,
    updated_at
)
VALUES (
    'DESCONTO10',
    'Desconto R$ 15,00',
    'Cupom de desconto fixo de R$ 15,00',
    'FIXED',
    1500.00,
    0.00,
    NULL,
    NULL,
    NULL,
    NOW(),
    NULL,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (code) DO UPDATE
SET
    name                     = EXCLUDED.name,
    description              = EXCLUDED.description,
    discount_type            = EXCLUDED.discount_type,
    discount_value           = EXCLUDED.discount_value,
    minimum_order_value      = EXCLUDED.minimum_order_value,
    maximum_discount_value   = EXCLUDED.maximum_discount_value,
    usage_limit              = EXCLUDED.usage_limit,
    usage_limit_per_user     = EXCLUDED.usage_limit_per_user,
    valid_until              = EXCLUDED.valid_until,
    active                   = EXCLUDED.active,
    updated_at               = NOW();
