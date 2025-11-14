# Documentação de Endpoints da API

## 📋 Visão Geral

Este documento lista todos os endpoints da API `/api/v1` e seu status de implementação no frontend.

**Sistema Global:** Todas as requisições usam o prefixo `/api/v1` através do arquivo `src/api/http.ts`.

---

## ✅ Endpoints Implementados e Funcionando

### Checkout

| Endpoint | Método | Status | Arquivo | Descrição |
|----------|--------|--------|---------|-----------|
| `/api/v1/checkout/card` | POST | ✅ | `CardPaymentPage.tsx` | Processa pagamento com cartão de crédito |
| `/api/v1/checkout/pix` | POST | ✅ | `PixPaymentPage.tsx` | Processa pagamento via PIX |

### Livros

| Endpoint | Método | Status | Arquivo | Descrição |
|----------|--------|--------|---------|-----------|
| `/api/v1/books` | GET | ✅ | `BooksListPage.tsx` | Lista todos os livros (com fallback local) |
| `/api/v1/books/{id}` | GET | ✅ | `api/stock.ts` | Busca informações de um livro específico |

### Cupons

| Endpoint | Método | Status | Arquivo | Descrição |
|----------|--------|--------|---------|-----------|
| `/api/v1/coupons/validate` | POST | ✅ | `hooks/useCoupon.ts` | Valida e aplica cupom de desconto |
| `/api/v1/coupons/{code}` | GET | ✅ | `hooks/useCoupon.ts` | Busca informações de um cupom (função `getCouponInfo()`) |

### Pedidos

| Endpoint | Método | Status | Arquivo | Descrição |
|----------|--------|--------|---------|-----------|
| `/api/v1/orders/{orderId}/events` | GET | ✅ | `PixPaymentPage.tsx` | Server-Sent Events (SSE) para atualizações de status do pedido |

### Privacidade

| Endpoint | Método | Status | Arquivo | Descrição |
|----------|--------|--------|---------|-----------|
| `/api/v1/privacy/consent` | GET | ✅ | `CookieConsent.tsx` | Busca status de consentimento de cookies |
| `/api/v1/privacy/consent` | POST | ✅ | `CookieConsent.tsx` | Salva consentimento de cookies (aceitar/recusar) |

---

## 🚫 Endpoints que NÃO são usados no Frontend

### Webhooks (Backend recebe, não precisa no frontend)

**Por que não são chamados?**
- Webhooks são endpoints que o **backend recebe**, não que o frontend chama
- A Efí Bank (gateway de pagamento) envia notificações diretamente para esses endpoints do backend
- O frontend não tem acesso direto a esses webhooks - eles são chamados pela Efí via HTTP POST
- O backend processa essas notificações e atualiza o status dos pedidos internamente
- O frontend recebe atualizações via **Server-Sent Events (SSE)** no endpoint `/api/v1/orders/{orderId}/events`

**Fluxo:** `Efí Bank` → `POST /api/v1/webhooks/*` → `Backend processa` → `Frontend recebe atualização via SSE`

| Controller | Método | Endpoint | Motivo |
|------------|--------|----------|--------|
| `efi-pix-send-webhook-controller` | POST | `/api/v1/webhooks/payout/pix` | **Efí → Backend**: Notificações de saque PIX processado |
| `pix-efi-webhook-controller` | POST | `/api/v1/webhooks/payment/pix` | **Efí → Backend**: Notificações de pagamento PIX recebido |
| `payment-webhook-controller` | POST | `/api/v1/webhooks/payment/payout/pix` | **Efí → Backend**: Notificações de pagamento/saque PIX |
| `card-efi-webhook-controller` | POST | `/api/v1/webhooks/payment/card` | **Efí → Backend**: Notificações de pagamento com cartão |

### Endpoints Internos (Não devem ser chamados do frontend)

**Por que não são chamados?**
- Endpoints marcados como `/internal/` são para uso administrativo interno
- Podem ter autenticação/autorização diferente
- Não devem ser expostos publicamente no frontend por questões de segurança
- Usados apenas por sistemas administrativos ou scripts internos

| Controller | Método | Endpoint | Motivo |
|------------|--------|----------|--------|
| `manual-payout-controller` | POST | `/api/v1/internal/payouts/{orderId}/trigger` | **Uso administrativo**: Dispara pagamento manualmente. Requer permissões especiais. |

### Health Checks (Não precisam no frontend)

**Por que não são chamados?**
- Health checks são para monitoramento de infraestrutura
- Usados por sistemas de monitoramento (ex: Kubernetes, AWS ELB, etc.)
- O frontend não precisa verificar se o servidor está online - o navegador já faz isso automaticamente
- Se o servidor estiver offline, as requisições falharão naturalmente

| Controller | Método | Endpoint | Motivo |
|------------|--------|----------|--------|
| `health-controller` | GET | `/health` | **Monitoramento**: Verifica se o servidor está respondendo |
| `health-controller` | GET | `/` | **Monitoramento**: Endpoint raiz para health check |

---

## 📝 Funcionalidades Calculadas Localmente

### Cálculo de Frete

**Status:** Calculado localmente no frontend (não usa endpoint da API)

**Arquivo:** `src/utils/freteUtils.ts`

**Como funciona:**
- Usa uma função simulada baseada em:
  - Distância (prefixo do CEP de origem vs destino)
  - Peso total dos livros no carrinho
- **Não há endpoint de API** para cálculo de frete
- O valor calculado é enviado junto com o checkout (`shipping` no payload)

**Nota:** O único uso de API externa relacionado a CEP é o **ViaCEP** (API pública) para buscar endereço completo pelo CEP, mas isso não é um endpoint do nosso backend.

---

## 🔧 Sistema Global de API

### Arquivo Central: `src/api/http.ts`

Todas as requisições passam pelo sistema global que:
- Adiciona automaticamente o prefixo `/api/v1`
- Normaliza paths (remove `/api/` ou `/api/v1` se já estiver presente)
- Fornece funções utilitárias: `apiGet`, `apiPost`, `apiPut`, `apiDelete`, `apiPatch`
- Exporta `buildApiUrl()` para casos especiais (ex: EventSource/SSE)

### Exemplo de Uso

```typescript
import { apiGet, apiPost } from '../api/http';

// GET /api/v1/books
const books = await apiGet<Book[]>('/books');

// POST /api/v1/checkout/pix
const response = await apiPost<CheckoutResponse>('/checkout/pix', payload);

// Para EventSource (SSE)
import { buildApiUrl } from '../api/http';
const url = buildApiUrl('/orders/123/events');
const es = new EventSource(url);
```

---

## ✅ Resumo Final

- **Endpoints implementados:** 9
- **Endpoints não usados (webhooks):** 4
- **Endpoints não usados (internos):** 1
- **Endpoints não usados (health checks):** 2

**Total de endpoints do backend:** 16
**Total implementados no frontend:** 9 (todos os necessários)

**Todos os endpoints necessários para o funcionamento do frontend estão implementados e usando o sistema global `/api/v1`!** ✅

