-- Migration para adicionar colunas de cupom na tabela orders
-- V10 - Add coupon columns to orders table

-- Adicionar colunas de cupom na tabela orders
ALTER TABLE orders 
ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50),
ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(10, 2);

-- Adicionar comentários
COMMENT ON COLUMN orders.coupon_code IS 'Código do cupom aplicado no pedido';
COMMENT ON COLUMN orders.discount_amount IS 'Valor do desconto aplicado pelo cupom';

-- Criar índice para busca por cupom
CREATE INDEX IF NOT EXISTS idx_orders_coupon_code ON orders (coupon_code);
