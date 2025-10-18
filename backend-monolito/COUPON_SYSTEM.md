# Sistema de Cupons - Backend

## Visão Geral

Sistema completo de cupons de desconto implementado no backend, permitindo validação, cálculo de descontos e integração com os processos de checkout (PIX e Cartão).

## Funcionalidades Implementadas

### 1. Modelos de Dados

#### Coupon
- **Código único** para identificação
- **Tipos de desconto**: FIXED (valor fixo) ou PERCENTAGE (percentual)
- **Valor do desconto** configurável
- **Valor mínimo do pedido** para aplicação
- **Valor máximo de desconto** (para cupons percentuais)
- **Limites de uso**: total e por usuário
- **Período de validade** com datas de início e fim
- **Status ativo/inativo**

#### OrderCoupon
- **Histórico de cupons aplicados** em pedidos
- **Auditoria completa** com valores originais, descontos e totais finais
- **Relacionamento** com Order e Coupon

### 2. API Endpoints

#### POST `/api/coupons/validate`
Valida um cupom e calcula o desconto aplicável.

**Request:**
```json
{
  "code": "DESCONTO10",
  "orderTotal": 50.00,
  "userEmail": "user@example.com"
}
```

**Response (Sucesso):**
```json
{
  "valid": true,
  "coupon": {
    "id": 1,
    "code": "DESCONTO10",
    "name": "Desconto R$ 10,00",
    "description": "Cupom de desconto fixo",
    "discountType": "FIXED",
    "discountValue": 10.00,
    "minimumOrderValue": 0.00,
    "maximumDiscountValue": null
  },
  "discountAmount": 10.00,
  "finalTotal": 40.00,
  "errorMessage": null
}
```

**Response (Erro):**
```json
{
  "valid": false,
  "coupon": null,
  "discountAmount": 0.00,
  "finalTotal": 50.00,
  "errorMessage": "Cupom não encontrado ou inativo"
}
```

#### GET `/api/coupons/{code}`
Obtém informações de um cupom específico.

### 3. Integração com Checkout

#### Checkout PIX
- Validação automática de cupom no `PixCheckoutService`
- Cálculo correto do total final com desconto
- Criação de cobrança PIX com valor correto
- Salvamento do cupom aplicado no histórico

#### Checkout Cartão
- Validação automática de cupom no `CardCheckoutService`
- Cálculo correto do total final com desconto
- Criação de cobrança cartão com valor correto
- Salvamento do cupom aplicado no histórico

### 4. Validações Implementadas

#### Validações de Cupom
- ✅ Cupom existe e está ativo
- ✅ Cupom está dentro do período de validade
- ✅ Valor do pedido atende ao mínimo necessário
- ✅ Limite de uso total não foi excedido
- ✅ Limite de uso por usuário não foi excedido
- ✅ Cálculo correto do desconto (fixo ou percentual)
- ✅ Respeito ao valor máximo de desconto (cupons percentuais)

#### Validações de Pedido
- ✅ Total final não pode ser negativo
- ✅ Desconto não pode ser maior que o total do pedido
- ✅ Auditoria completa dos valores aplicados

### 5. Banco de Dados

#### Tabela `coupons`
```sql
CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('FIXED', 'PERCENTAGE')),
    discount_value BIGINT NOT NULL CHECK (discount_value >= 0),
    minimum_order_value BIGINT DEFAULT 0,
    maximum_discount_value BIGINT,
    usage_limit INTEGER,
    usage_limit_per_user INTEGER,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### Tabela `order_coupons`
```sql
CREATE TABLE order_coupons (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    original_total BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    final_total BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id, coupon_id)
);
```

### 6. Cupom Padrão

O sistema já inclui um cupom de exemplo:
- **Código**: `DESCONTO10`
- **Nome**: "Desconto R$ 10,00"
- **Tipo**: FIXED
- **Valor**: R$ 10,00
- **Valor mínimo**: R$ 0,00
- **Status**: Ativo

### 7. Uso no Frontend

Para usar o sistema de cupons no frontend:

1. **Validação**: Chame `POST /api/coupons/validate` antes do checkout
2. **Checkout**: Inclua o `couponCode` nos requests de checkout (PIX ou Cartão)
3. **Persistência**: O backend salva automaticamente o cupom aplicado

#### Exemplo de Request de Checkout com Cupom:
```json
{
  "firstName": "João",
  "lastName": "Silva",
  "email": "joao@example.com",
  "cpf": "12345678901",
  "cep": "01234567",
  "address": "Rua das Flores, 123",
  "city": "São Paulo",
  "state": "SP",
  "phone": "11999999999",
  "shipping": 10.00,
  "cartItems": [...],
  "total": 50.00,
  "couponCode": "DESCONTO10"
}
```

### 8. Próximos Passos

1. **Criação de cupons**: Implementar endpoints administrativos para criar/editar cupons
2. **Relatórios**: Implementar relatórios de uso de cupons
3. **Cupons por categoria**: Implementar cupons específicos para categorias de produtos
4. **Cupons por usuário**: Implementar cupons exclusivos por usuário
5. **Cupons de primeira compra**: Implementar cupons para novos usuários

## Arquivos Modificados/Criados

### Novos Arquivos
- `src/main/resources/db/migration/V7__create_coupons_table.sql`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/models/coupon/Coupon.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/models/coupon/OrderCoupon.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/repositories/CouponRepository.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/repositories/OrderCouponRepository.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/services/coupon/CouponService.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/controllers/coupon/CouponController.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/dto/coupon/CouponValidationRequestDto.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/dto/coupon/CouponValidationResponseDto.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/exceptions/CouponValidationException.kt`

### Arquivos Modificados
- `src/main/kotlin/com/luizgasparetto/backend/monolito/dto/pix/PixCheckoutRequest.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/dto/card/CardCheckoutRequest.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/models/order/Order.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/services/pix/PixCheckoutService.kt`
- `src/main/kotlin/com/luizgasparetto/backend/monolito/services/card/CardCheckoutService.kt`

## Testes Recomendados

1. **Validação de cupom válido**
2. **Validação de cupom inválido/expirado**
3. **Validação de limite de uso**
4. **Checkout PIX com cupom**
5. **Checkout Cartão com cupom**
6. **Checkout sem cupom (funcionalidade existente)**
7. **Cálculo correto de descontos fixos e percentuais**

O sistema está pronto para uso e totalmente integrado com os processos de checkout existentes!
