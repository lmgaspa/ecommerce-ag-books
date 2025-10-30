-- Migration para criar tabela de cupons
CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    
    -- Tipo de desconto: 'FIXED' (valor fixo) ou 'PERCENTAGE' (percentual)
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('FIXED', 'PERCENTAGE')),
    
    -- Valor do desconto (para FIXED: valor em centavos, para PERCENTAGE: percentual 0-100)
    discount_value BIGINT NOT NULL CHECK (discount_value >= 0),
    
    -- Valor mínimo do pedido para aplicar o cupom (em centavos)
    minimum_order_value BIGINT DEFAULT 0,
    
    -- Valor máximo de desconto (em centavos) - aplicado apenas para cupons percentuais
    maximum_discount_value BIGINT,
    
    -- Limite de uso total (null = ilimitado)
    usage_limit INTEGER,
    
    -- Limite de uso por usuário (null = ilimitado)
    usage_limit_per_user INTEGER,
    
    -- Data de início da validade
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Data de fim da validade (null = sem expiração)
    valid_until TIMESTAMP WITH TIME ZONE,
    
    -- Status do cupom
    active BOOLEAN NOT NULL DEFAULT true,
    
    -- Metadados de auditoria
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índices para performance
CREATE INDEX idx_coupons_code ON coupons(code);
CREATE INDEX idx_coupons_active ON coupons(active);
CREATE INDEX idx_coupons_valid_dates ON coupons(valid_from, valid_until);

-- Tabela para rastrear uso de cupons por pedido
CREATE TABLE order_coupons (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    
    -- Valores salvos no momento do pedido (para auditoria)
    original_total BIGINT NOT NULL, -- total original em centavos
    discount_amount BIGINT NOT NULL, -- valor do desconto aplicado em centavos
    final_total BIGINT NOT NULL,    -- total final após desconto em centavos
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(order_id, coupon_id)
);

-- Índices para order_coupons
CREATE INDEX idx_order_coupons_order_id ON order_coupons(order_id);
CREATE INDEX idx_order_coupons_coupon_id ON order_coupons(coupon_id);

-- Comentários para documentação
COMMENT ON TABLE coupons IS 'Tabela de cupons de desconto do e-commerce';
COMMENT ON COLUMN coupons.discount_type IS 'Tipo: FIXED (valor fixo) ou PERCENTAGE (percentual)';
COMMENT ON COLUMN coupons.discount_value IS 'Valor do desconto em centavos (FIXED) ou percentual 0-100 (PERCENTAGE)';
COMMENT ON COLUMN coupons.minimum_order_value IS 'Valor mínimo do pedido em centavos para aplicar o cupom';
COMMENT ON COLUMN coupons.maximum_discount_value IS 'Valor máximo de desconto em centavos (apenas para PERCENTAGE)';

COMMENT ON TABLE order_coupons IS 'Histórico de cupons aplicados em pedidos';
COMMENT ON COLUMN order_coupons.original_total IS 'Total original do pedido em centavos';
COMMENT ON COLUMN order_coupons.discount_amount IS 'Valor do desconto aplicado em centavos';
COMMENT ON COLUMN order_coupons.final_total IS 'Total final após desconto em centavos';

-- Inserir cupom padrão DESCONTO10
INSERT INTO coupons (code, name, description, discount_type, discount_value, minimum_order_value, valid_from, active) 
VALUES ('DESCONTO10', 'Desconto R$ 10,00', 'Cupom de desconto fixo de R$ 10,00', 'FIXED', 1000, 0, CURRENT_TIMESTAMP, true);
