-- Corrigir cupom DESCONTO10 para desconto de R$ 15,00
-- O cupom estava configurado com desconto de R$ 10,00 (1000 centavos)
-- Agora será R$ 15,00 (1500 centavos)

UPDATE coupons 
SET 
    discount_value = 1500,  -- R$ 15,00 em centavos
    name = 'Desconto R$ 15,00',
    description = 'Cupom de desconto fixo de R$ 15,00',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'DESCONTO10';

-- Verificar se a atualização foi feita
SELECT 
    code,
    name,
    discount_value,
    discount_type,
    minimum_order_value,
    active
FROM coupons 
WHERE code = 'DESCONTO10';
