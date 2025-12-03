// src/pages/CardPaymentPage.tsx
import { useEffect, useMemo, useState, useContext } from "react";
import { useNavigate } from "react-router-dom";

// ---- Efí (via NPM package) ----
import {
  tokenize,
  getInstallments,
  verifyBrandFromNumber,
  isScriptBlocked,
  type CardBrand,
  type InstallmentItem,
} from "../services/efiCard";

import { cookieStorage } from "../utils/cookieUtils";
import { analytics, mapCartItems } from "../analytics";
import { useCoupon } from "../hooks/useCoupon";
import { apiPost } from "../api/http";

import type { CartItem } from "../context/CartTypes";
import type { CheckoutFormData } from "../types/CheckoutTypes";
import { CartContext } from "../context/CartContext";
import { OrderSummary } from "../components/OrderSummary";
import ReviewPurchaseButton from "../components/common/ReviewPurchaseButton";

/* ===================== Local types & helpers ===================== */

interface CardCheckoutResponse {
  success: boolean;
  message: string;
  orderId: string;
  chargeId?: string | null;
  status?: string | null;
  reserveExpiresAt?: string | null;
  ttlSeconds?: number | null;
  warningAt?: number | null;
  securityWarningAt?: number | null;
}

type BrandUI = CardBrand;

// Read env in a way that survives Vite type narrowing during SSR/CSR builds.
const ENV = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};

const PAYEE_CODE = ENV.VITE_EFI_PAYEE_CODE?.trim();

if (!PAYEE_CODE) {
  throw new Error("VITE_EFI_PAYEE_CODE não configurado. Configure a variável de ambiente.");
}

// TypeScript assertion: PAYEE_CODE is guaranteed to be string after the check above
const PAYEE_CODE_ASSERTED: string = PAYEE_CODE;

// If VITE_EFI_SANDBOX === "true" => sandbox, otherwise production.
const EFI_ENV: "production" | "sandbox" =
  String(ENV.VITE_EFI_SANDBOX ?? "false").toLowerCase() === "true"
    ? "sandbox"
    : "production";

/* ---------- Formatting helpers ---------- */
function formatCardNumber(value: string, brand: BrandUI): string {
  const digits = value.replace(/\D/g, "");
  if (brand === "amex") {
    const d = digits.slice(0, 15);
    return d
      .replace(/^(\d{1,4})(\d{1,6})?(\d{1,5})?$/, (_, a, b, c) =>
        [a, b, c].filter(Boolean).join(" ")
      )
      .trim();
  }
  return digits.slice(0, 16).replace(/(\d{4})(?=\d)/g, "$1 ").trim();
}

function formatMonthStrict(value: string): string {
  let d = value.replace(/\D/g, "").slice(0, 2);
  if (d.length === 1) {
    if (Number(d) > 1) d = `0${d}`;
  } else if (d.length === 2) {
    const n = Number(d);
    if (n === 0) d = "01";
    else if (n > 12) d = "12";
  }
  return d;
}

function formatYearYYYY(value: string): string {
  return value.replace(/\D/g, "").slice(0, 4);
}

function formatCvv(value: string, brand: BrandUI): string {
  const max = brand === "amex" ? 4 : 3;
  return value.replace(/\D/g, "").slice(0, max);
}

function isValidLuhn(numDigits: string): boolean {
  let sum = 0,
    dbl = false;
  for (let i = numDigits.length - 1; i >= 0; i--) {
    let n = Number(numDigits[i]);
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}

function readJson<T>(key: string, fallback: T): T {
  return cookieStorage.get<T>(key, fallback);
}

const toYYYY = (yyOrYYYY: string) => {
  const d = yyOrYYYY.replace(/\D/g, "");
  return d.length === 2 ? `20${d}` : d.slice(0, 4);
};

/* ===================== Component ===================== */

interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string;
  expirationYear: string;
  cvv: string;
  brand: BrandUI;
}

export default function CardPaymentPage() {
  const navigate = useNavigate();
  const { getDiscountAmount, couponCode, clearCoupon, isValid: isCouponValid } = useCoupon();
  const cartContext = useContext(CartContext);

  // 1ª fonte: CartContext; fallback: cookie
  const cart: CartItem[] = useMemo(
    () => cartContext?.cartItems ?? readJson<CartItem[]>("cart", []),
    [cartContext?.cartItems]
  );

  const form: CheckoutFormData = useMemo(
    () => readJson<CheckoutFormData>("checkoutForm", {} as CheckoutFormData),
    []
  );

  const shipping = Number(form?.shipping ?? 0);
  const subtotal = cart.reduce((acc, i) => acc + i.price * i.quantity, 0);
  const desconto = getDiscountAmount(subtotal);
  const total = subtotal + shipping - desconto;

  // Valida se o cupom ainda está válido para exibição visual
  const finalCouponCode = (isCouponValid && couponCode) ? couponCode : null;
  const finalDiscount = finalCouponCode ? desconto : 0;

  // Guard: if cart empty or total invalid, go back to checkout
  useEffect(() => {
    if (!Array.isArray(cart) || cart.length === 0 || total <= 0) {
      navigate("/checkout");
    }
  }, [cart, total, navigate]);

  const [brand, setBrand] = useState<BrandUI>("visa");
  const [card, setCard] = useState<CardData>({
    number: "",
    holderName: "",
    expirationMonth: "",
    expirationYear: "",
    cvv: "",
    brand: "visa",
  });

  const [installments, setInstallments] = useState<number>(1);
  const [installmentOptions, setInstallmentOptions] = useState<InstallmentItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Estados para controle de expiração
  const [checkoutResponse, setCheckoutResponse] = useState<CardCheckoutResponse | null>(null);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  const [showWarning, setShowWarning] = useState(false);
  const [showSecurityWarning, setShowSecurityWarning] = useState(false);
  const [isExpired, setIsExpired] = useState(false);

  const numberDigits = card.number.replace(/\D/g, "");
  const cvvLen = brand === "amex" ? 4 : 3;

  // Timer para controle de expiração
  useEffect(() => {
    if (!checkoutResponse?.ttlSeconds) return;

    const ttlSeconds = checkoutResponse.ttlSeconds;
    const warningAt = checkoutResponse.warningAt || 60;
    const securityWarningAt = checkoutResponse.securityWarningAt || 60;

    setTimeLeft(ttlSeconds);

    const interval = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev === null) return null;
        const newTime = prev - 1;

        if (newTime <= securityWarningAt && !showSecurityWarning) {
          setShowSecurityWarning(true);
        }
        if (newTime <= warningAt && !showWarning) {
          setShowWarning(true);
        }
        if (newTime <= 0) {
          setIsExpired(true);
          setShowWarning(false);
          setShowSecurityWarning(false);
          return 0;
        }
        return newTime;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [checkoutResponse, showWarning, showSecurityWarning]);

  // Warn if Efí fingerprint/script is blocked by extensions
  useEffect(() => {
    (async () => {
      try {
        const blocked = await isScriptBlocked();
        if (blocked) console.warn("Efí fingerprint/script blocked by an extension!");
      } catch {
        /* no-op */
      }
    })();
  }, []);

  // Detect brand from card IIN
  useEffect(() => {
    (async () => {
      if (numberDigits.length < 6) return;
      try {
        const b = await verifyBrandFromNumber(numberDigits);
        if (b !== "unsupported" && b !== "undefined") {
          setBrand(b as CardBrand);
          setCard((prev) => ({
            ...prev,
            brand: b as BrandUI,
            number: formatCardNumber(prev.number, b as BrandUI),
            cvv: formatCvv(prev.cvv, b as BrandUI),
          }));
        }
      } catch {
        /* no-op */
      }
    })();
  }, [numberDigits]);

  // Fetch installments from Efí using ENV
  // Usa o total COM desconto para calcular as parcelas corretamente
  useEffect(() => {
    (async () => {
      try {
        const cents = Math.round(total * 100);
        if (cents <= 0) {
          setInstallmentOptions([]);
          setInstallments(1);
          return;
        }
        const resp = await getInstallments(
          PAYEE_CODE_ASSERTED,
          EFI_ENV,
          brand as CardBrand,
          cents
        );
        setInstallmentOptions(resp.installments || []);
        if (resp.installments?.length) {
          setInstallments(resp.installments[0].installment);
        } else {
          setInstallments(1);
        }
      } catch (e) {
        console.error("Failed to fetch installments:", e);
        setInstallmentOptions([]);
        setInstallments(1);
      }
    })();
  }, [brand, total, desconto]);

  const onChangeBrand = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newBrand = e.target.value as BrandUI;
    setBrand(newBrand);
    setCard((prev) => ({
      ...prev,
      brand: newBrand,
      number: formatCardNumber(prev.number, newBrand),
      cvv: formatCvv(prev.cvv, newBrand),
    }));
  };

  const onChangeNumber = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = brand;
    setCard((prev) => ({ ...prev, number: formatCardNumber(e.target.value, b) }));
  };

  const onChangeHolder = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, holderName: e.target.value }));
  };

  const onChangeMonth = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationMonth: formatMonthStrict(e.target.value) }));
  };

  const onChangeYear = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationYear: formatYearYYYY(e.target.value) }));
  };

  const onBlurYear = () => {
    setCard((prev) => ({ ...prev, expirationYear: toYYYY(prev.expirationYear) }));
  };

  const onChangeCvv = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = brand;
    setCard((prev) => ({ ...prev, cvv: formatCvv(e.target.value, b) }));
  };

  // Basic validations
  const lenOk =
    (brand === "amex" && numberDigits.length === 15) ||
    (brand !== "amex" && numberDigits.length >= 14 && numberDigits.length <= 16);
  const luhnOk = lenOk && isValidLuhn(numberDigits);
  const holderOk = card.holderName.trim().length > 0;
  const monthOk =
    /^\d{2}$/.test(card.expirationMonth) &&
    Number(card.expirationMonth) >= 1 &&
    Number(card.expirationMonth) <= 12;
  const yearOk = /^\d{4}$/.test(card.expirationYear);
  const cvvOk = new RegExp(`^\\d{${cvvLen}}$`).test(card.cvv);

  // CPF: require at least 11 digits (keeps backend as source of truth)
  const holderDocument = (form.cpf ?? "").replace(/\D/g, "");
  const docOk = holderDocument.length >= 11;

  // Validação considerando expiração
  const canPay =
    !loading &&
    luhnOk &&
    holderOk &&
    monthOk &&
    yearOk &&
    cvvOk &&
    docOk &&
    total > 0 &&
    !isExpired;

  const selectedInstallment = installmentOptions.find(
    (opt) => opt.installment === installments
  );

  const perInstallment = useMemo(() => {
    // Sempre usa o total atual (com desconto) para calcular o valor por parcela
    // A API pode retornar valores baseados em um total antigo, então recalculamos
    if (installments <= 1) return total;
    // Se temos uma opção selecionada da API, mas o total mudou, recalcula baseado no total atual
    if (selectedInstallment) {
      // Recalcula proporcionalmente se o total mudou
      const apiTotal = selectedInstallment.value / 100 * installments;
      if (Math.abs(apiTotal - total) > 0.01) {
        // Total mudou (desconto foi aplicado), recalcula baseado no total atual
        return Math.round((total / installments) * 100) / 100;
      }
      return selectedInstallment.value / 100;
    }
    return Math.round((total / installments) * 100) / 100;
  }, [selectedInstallment, installments, total]);

  const formatTimeLeft = (seconds: number): string => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, "0")}`;
  };

  const handlePay = async () => {
    if (!canPay) return;
    setLoading(true);
    setErrorMsg(null);

    try {
      // GA4: add_payment_info – OCP: só conversa com fachada analytics
      try {
        analytics.addPaymentInfo({
          items: mapCartItems(cart),
          value: Number(total),
          payment_type: "credit_card",
          installments,
          currency: "BRL",
        });
      } catch {
        /* no-op */
      }

      // Tokenize with Efí
      const tokenResp = await tokenize(PAYEE_CODE_ASSERTED, EFI_ENV, {
        brand: brand as CardBrand,
        number: numberDigits,
        cvv: card.cvv,
        expirationMonth: card.expirationMonth,
        expirationYear: toYYYY(card.expirationYear),
        holderName: card.holderName,
        holderDocument,
        reuse: false,
      });

      const payload = {
        ...form,
        payment: "card",
        paymentToken: tokenResp.payment_token,
        installments,
        cartItems: cart,
        // backend usa total original SEM desconto para validar e aplicar cupom
        total: subtotal + shipping,
        shipping,
        discount: finalDiscount,
        couponCode: finalCouponCode,
      };

      const data: CardCheckoutResponse = await apiPost<CardCheckoutResponse>(
        "/checkout/card",
        payload
      );

      // Guardamos para mostrar TTL/avisos se fizer sentido
      setCheckoutResponse(data);

      const paidStatuses = ["PAID", "APPROVED", "CAPTURED", "CONFIRMED"];
      const isPaid = data.status
        ? paidStatuses.includes(String(data.status).toUpperCase())
        : false;

      // Se teve sucesso e tem orderId, redireciona para PedidoConfirmado
      // (o backend pode processar pagamentos com cupom de forma diferente e não retornar status)
      // Só não redireciona se o status indicar explicitamente um erro (ex: "FAILED", "REJECTED")
      const errorStatuses = ["FAILED", "REJECTED", "CANCELLED", "CANCELED"];
      const hasErrorStatus = data.status
        ? errorStatuses.includes(String(data.status).toUpperCase())
        : false;

      if (data.success && data.orderId && !hasErrorStatus) {
        // ✅ A partir daqui é COMPRA CONCLUÍDA: podemos limpar o carrinho e o cupom
        if (cartContext?.clearCart) {
          cartContext.clearCart();
        } else {
          cookieStorage.remove("cart");
        }

        // Limpa o cupom após compra bem-sucedida (idempotência)
        if (couponCode) {
          clearCoupon();
        }

        // Snapshot para PedidoConfirmado disparar analytics.purchase
        const purchasePayload = {
          transaction_id: String(data.orderId),
          value: Number(total),
          currency: "BRL",
          shipping: Number(shipping || 0),
          tax: 0,
          items: mapCartItems(cart),
          payment_type: "credit_card",
        };
        sessionStorage.setItem(
          "ga_purchase_payload",
          JSON.stringify(purchasePayload)
        );

        // Vai para a tela de confirmação, marcada como paga
        navigate(
          `/pedido-confirmado?orderId=${data.orderId}&payment=card&paid=true`
        );
        return;
      }

      // Se não teve sucesso ou não veio orderId, mostra erro
      if (!data.success || !data.orderId) {
        setErrorMsg(
          data.message ||
            "Não foi possível processar o pagamento. Verifique os dados e tente novamente."
        );
        return;
      }

      // Se tem orderId mas status não está aprovado, mostra mensagem de aguardando
      if (data.orderId && !isPaid && data.status) {
        setErrorMsg(
          data.message ||
            "Pagamento ainda não foi aprovado. Tente novamente em alguns instantes ou use outra forma de pagamento."
        );
        return;
      }
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Falha no pagamento.");
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto p-6">
      <h2 className="text-xl font-semibold mb-4 text-center">
        Pagamento com Cartão
      </h2>

      {checkoutResponse && !isExpired && (
        <div className="bg-blue-50 text-blue-700 p-3 mb-4 rounded-lg border border-blue-200">
          <div className="flex items-center">
            <span className="text-lg mr-2">⏰</span>
            <div>
              <p className="font-medium">Pagamento expira em 15 minutos</p>
              <p className="text-sm">
                Complete o pagamento antes do tempo expirar
              </p>
            </div>
          </div>
        </div>
      )}

      {showWarning && !isExpired && (
        <div className="bg-orange-50 text-orange-700 p-3 mb-4 rounded-lg border border-orange-200">
          <div className="flex items-center">
            <span className="text-lg mr-2">⚠️</span>
            <div>
              <p className="font-medium">
                Cartão será invalidado em{" "}
                {timeLeft !== null ? formatTimeLeft(timeLeft) : "--:--"}!
              </p>
              <p className="text-sm">Complete o pagamento agora</p>
            </div>
          </div>
        </div>
      )}

      {showSecurityWarning && !isExpired && (
        <div className="bg-red-50 text-red-700 p-3 mb-4 rounded-lg border border-red-200">
          <div className="flex items-center">
            <span className="text-lg mr-2">🚨</span>
            <div>
              <p className="font-medium">
                Por questões de segurança, o cartão foi invalidado!
              </p>
              <p className="text-sm">
                Complete o pagamento agora ou será necessário reiniciar
              </p>
            </div>
          </div>
        </div>
      )}

      {isExpired && (
        <div className="bg-red-50 text-red-700 p-3 mb-4 rounded-lg border border-red-200">
          <div className="flex items-center">
            <span className="text-lg mr-2">❌</span>
            <div>
              <p className="font-medium">
                Cartão invalidado por questões de segurança
              </p>
              <p className="text-sm">
                O cartão foi invalidado aos 60 segundos restantes. Reinicie o
                processo de pagamento
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 🔁 Usa o componente compartilhado de resumo */}
      <OrderSummary
        subtotal={subtotal}
        shipping={shipping}
        discount={desconto}
        total={total}
      />

      {/* 🔁 Botão global reutilizável */}
      <ReviewPurchaseButton />

      {/* 🎯 INDICADOR VISUAL DO CUPOM - SEMPRE MOSTRA SE HOUVER DESCONTO */}
      {desconto > 0 ? (
        <div className="bg-green-50 border-2 border-green-500 rounded-lg p-4 mb-4 animate-pulse">
          <div className="flex items-center gap-2">
            <span className="text-2xl">✅</span>
            <div>
              <p className="font-bold text-green-800">
                {couponCode ? `Cupom Aplicado: ${couponCode}` : "Desconto Aplicado"}
              </p>
              <p className="text-sm text-green-700">
                Desconto de R$ {desconto.toFixed(2).replace(".", ",")} aplicado com sucesso!
              </p>
            </div>
          </div>
        </div>
      ) : couponCode ? (
        <div className="bg-yellow-50 border-2 border-yellow-500 rounded-lg p-4 mb-4">
          <div className="flex items-center gap-2">
            <span className="text-2xl">⚠️</span>
            <div>
              <p className="font-bold text-yellow-800">
                Cupom não será aplicado
              </p>
              <p className="text-sm text-yellow-700">
                O cupom "{couponCode}" não está válido ou expirou. O pagamento será processado sem desconto.
              </p>
            </div>
          </div>
        </div>
      ) : null}

      <label className="block text-sm font-medium mb-1">Bandeira</label>
      <select
        value={brand}
        onChange={onChangeBrand}
        className="border p-2 w-full mb-4 rounded"
      >
        <option value="visa">Visa</option>
        <option value="mastercard">Mastercard</option>
        <option value="amex">American Express</option>
        <option value="elo">Elo</option>
        <option value="diners">Diners</option>
      </select>

      <input
        value={card.number}
        onChange={onChangeNumber}
        placeholder={
          brand === "amex"
            ? "Número do cartão (ex.: 3714 496353 98431)"
            : "Número do cartão (ex.: 4111 1111 1111 1111)"
        }
        className="border p-2 w-full mb-2 rounded"
        inputMode="numeric"
        autoComplete="cc-number"
      />

      <input
        value={card.holderName}
        onChange={onChangeHolder}
        placeholder="Nome impresso"
        className="border p-2 w-full mb-2 rounded"
        autoComplete="cc-name"
      />

      <div className="flex gap-2">
        <input
          value={card.expirationMonth}
          onChange={onChangeMonth}
          placeholder="MM"
          className="border p-2 w-1/2 mb-2 rounded"
          inputMode="numeric"
          autoComplete="cc-exp-month"
        />
        <input
          value={card.expirationYear}
          onChange={onChangeYear}
          onBlur={onBlurYear}
          placeholder="AAAA"
          className="border p-2 w-1/2 mb-2 rounded"
          inputMode="numeric"
          autoComplete="cc-exp-year"
        />
      </div>

      <input
        value={card.cvv}
        onChange={onChangeCvv}
        placeholder={`CVV (${cvvLen} dígitos)`}
        className="border p-2 w-full mb-4 rounded"
        inputMode="numeric"
        autoComplete="cc-csc"
      />

      <label className="block text-sm font-medium mb-1">Parcelas</label>
      <select
        className="border p-2 w-full rounded mb-2"
        value={installments}
        onChange={(e) => setInstallments(Number(e.target.value))}
      >
        {installmentOptions.length > 0
          ? installmentOptions.map((opt) => {
              // Recalcula o valor por parcela baseado no total atual (com desconto)
              const apiTotal = (opt.value / 100) * opt.installment;
              const adjustedValue = Math.abs(apiTotal - total) > 0.01 
                ? total / opt.installment 
                : opt.value / 100;
              return (
                <option value={opt.installment} key={opt.installment}>
                  {opt.installment}x de R$ {adjustedValue.toFixed(2)}{" "}
                  {opt.has_interest ? " (c/ juros)" : " (s/ juros)"}
                </option>
              );
            })
          : [1, 2, 3, 4, 5, 6].map((n) => (
              <option value={n} key={n}>
                {n}x
              </option>
            ))}
      </select>
      <p className="text-sm text-gray-600 mb-4">
        {installments}x de R$ {perInstallment.toFixed(2)} (total R${" "}
        {total.toFixed(2)})
      </p>

      {!docOk && (
        <div className="bg-yellow-50 text-yellow-700 p-2 mb-3 rounded">
          Informe um CPF válido no passo anterior para continuar.
        </div>
      )}

      {errorMsg && (
        <div className="bg-red-50 text-red-600 p-2 mb-4 rounded">
          {errorMsg}
        </div>
      )}

      <button
        disabled={!canPay}
        onClick={handlePay}
        className={`bg-blue-600 text-white py-2 w-full rounded ${
          canPay ? "hover:bg-blue-500" : "opacity-50 cursor-not-allowed"
        }`}
      >
        {loading
          ? "Processando..."
          : isExpired
          ? "Pagamento Expirado"
          : "Pagar com Cartão"}
      </button>
    </div>
  );
}
