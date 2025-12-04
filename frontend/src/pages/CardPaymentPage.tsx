// src/pages/CardPaymentPage.tsx - REFATORADO
import { useEffect, useMemo, useState, useContext } from "react";
import { useNavigate } from "react-router-dom";

import { tokenize, verifyBrandFromNumber, isScriptBlocked, type CardBrand } from "../services/efiCard";
import { cookieStorage } from "../utils/cookieUtils";
import { analytics, mapCartItems } from "../analytics";
import { useCoupon } from "../hooks/useCoupon";
import { apiPost } from "../api/http";

// Custom Hooks
import { useCardFormatting, type CardData } from "../hooks/useCardFormatting";
import { useCardValidation } from "../hooks/useCardValidation";
import { useInstallments } from "../hooks/useInstallments";
import { usePaymentTimer } from "../hooks/usePaymentTimer";

// Components
import { CardForm } from "../components/payment/card/CardForm";
import { InstallmentSelector } from "../components/payment/card/InstallmentSelector";
import { CardPaymentWarnings } from "../components/payment/card/CardPaymentWarnings";
import { CouponDisplay } from "../components/payment/card/CouponDisplay";

import type { CartItem } from "../context/CartTypes";
import type { CheckoutFormData } from "../types/CheckoutTypes";
import { CartContext } from "../context/CartContext";
import { OrderSummary } from "../components/OrderSummary";
import ReviewPurchaseButton from "../components/common/ReviewPurchaseButton";

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
  code?: string;
  gatewayCode?: string;
}

const ENV = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};
const PAYEE_CODE = ENV.VITE_EFI_PAYEE_CODE?.trim();
if (!PAYEE_CODE) throw new Error("VITE_EFI_PAYEE_CODE não configurado");
const PAYEE_CODE_ASSERTED: string = PAYEE_CODE; // Type assertion after validation
const EFI_ENV: "production" | "sandbox" = String(ENV.VITE_EFI_SANDBOX ?? "false").toLowerCase() === "true" ? "sandbox" : "production";

export default function CardPaymentPage() {
  const navigate = useNavigate();
  const { getDiscountAmount, couponCode, clearCoupon, isValid: isCouponValid } = useCoupon();
  const cartContext = useContext(CartContext);

  const cart: CartItem[] = useMemo(
    () => cartContext?.cartItems ?? cookieStorage.get<CartItem[]>("cart", []),
    [cartContext?.cartItems]
  );

  const form: CheckoutFormData = useMemo(
    () => cookieStorage.get<CheckoutFormData>("checkoutForm", {} as CheckoutFormData),
    []
  );

  const shipping = Number(form?.shipping ?? 0);
  const subtotal = cart.reduce((acc, i) => acc + i.price * i.quantity, 0);
  const desconto = getDiscountAmount(subtotal);
  const total = subtotal + shipping - desconto;
  const finalCouponCode = isCouponValid && couponCode ? couponCode : null;
  const finalDiscount = finalCouponCode ? desconto : 0;

  // State
  const [brand, setBrand] = useState<CardBrand>("visa");
  const [card, setCard] = useState<CardData>({
    number: "",
    holderName: "",
    expirationMonth: "",
    expirationYear: "",
    cvv: "",
    brand: "visa",
  });
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [checkoutResponse, setCheckoutResponse] = useState<CardCheckoutResponse | null>(null);

  // Custom Hooks
  const { formatCardNumber, formatMonth, formatYear, formatCvv, toYYYY } = useCardFormatting(brand);
  const holderDocument = (form.cpf ?? "").replace(/\D/g, "");
  const validation = useCardValidation(card, brand, holderDocument);
  const installmentsHook = useInstallments({ total, brand, payeeCode: PAYEE_CODE_ASSERTED, efiEnv: EFI_ENV });
  const timer = usePaymentTimer(
    checkoutResponse?.ttlSeconds ?? null,
    checkoutResponse?.warningAt ?? 60,
    checkoutResponse?.securityWarningAt ?? 60
  );

  // Guard: redirect if cart invalid
  useEffect(() => {
    if (!Array.isArray(cart) || cart.length === 0 || total <= 0) {
      navigate("/checkout");
    }
  }, [cart, total, navigate]);

  // Warn if Efí script blocked
  useEffect(() => {
    (async () => {
      try {
        if (await isScriptBlocked()) console.warn("Efí fingerprint/script blocked!");
      } catch { }
    })();
  }, []);

  // Auto-detect brand from card number
  useEffect(() => {
    (async () => {
      if (validation.numberDigits.length < 6) return;
      try {
        const b = await verifyBrandFromNumber(validation.numberDigits);
        if (b !== "unsupported" && b !== "undefined") {
          setBrand(b as CardBrand);
          setCard((prev) => ({
            ...prev,
            brand: b as CardBrand,
            number: formatCardNumber(prev.number),
            cvv: formatCvv(prev.cvv),
          }));
        }
      } catch { }
    })();
  }, [validation.numberDigits, formatCardNumber, formatCvv]);

  // Event handlers
  const onChangeBrand = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newBrand = e.target.value as CardBrand;
    setBrand(newBrand);
    setCard((prev) => ({ ...prev, brand: newBrand }));
  };

  const onChangeNumber = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, number: formatCardNumber(e.target.value) }));
  };

  const onChangeHolder = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, holderName: e.target.value }));
  };

  const onChangeMonth = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationMonth: formatMonth(e.target.value) }));
  };

  const onChangeYear = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationYear: formatYear(e.target.value) }));
  };

  const onBlurYear = () => {
    setCard((prev) => ({ ...prev, expirationYear: toYYYY(prev.expirationYear) }));
  };

  const onChangeCvv = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, cvv: formatCvv(e.target.value) }));
  };

  const canPay = !loading && validation.isValid && total > 0 && !timer.isExpired;

  const handlePay = async () => {
    if (!canPay) return;
    setLoading(true);
    setErrorMsg(null);

    try {
      // GA4
      try {
        analytics.addPaymentInfo({
          items: mapCartItems(cart),
          value: Number(total),
          payment_type: "credit_card",
          installments: installmentsHook.installments,
          currency: "BRL",
        });
      } catch { }

      // Tokenize
      const tokenResp = await tokenize(PAYEE_CODE_ASSERTED, EFI_ENV, {
        brand,
        number: validation.numberDigits,
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
        installments: installmentsHook.installments,
        cartItems: cart,
        total: subtotal + shipping,
        shipping,
        discount: finalDiscount,
        couponCode: finalCouponCode,
      };

      const data: CardCheckoutResponse = await apiPost<CardCheckoutResponse>("/checkout/card", payload);
      setCheckoutResponse(data);

      const paidStatuses = ["PAID", "APPROVED", "CAPTURED", "CONFIRMED"];
      const isPaid = data.status ? paidStatuses.includes(String(data.status).toUpperCase()) : false;
      const errorStatuses = ["FAILED", "REJECTED", "CANCELLED", "CANCELED"];
      const hasErrorStatus = data.status ? errorStatuses.includes(String(data.status).toUpperCase()) : false;

      if (data.success && data.orderId && !hasErrorStatus) {
        cartContext?.clearCart?.() ?? cookieStorage.remove("cart");
        if (couponCode) clearCoupon();

        sessionStorage.setItem(
          "ga_purchase_payload",
          JSON.stringify({
            transaction_id: String(data.orderId),
            value: Number(total),
            currency: "BRL",
            shipping: Number(shipping || 0),
            tax: 0,
            items: mapCartItems(cart),
            payment_type: "credit_card",
          })
        );

        navigate(`/pedido-confirmado?orderId=${data.orderId}&payment=card&paid=true`);
        return;
      }

      if (!data.success || !data.orderId) {
        setErrorMsg(data.message || "Não foi possível processar o pagamento.");
        return;
      }

      if (data.orderId && !isPaid && data.status) {
        setErrorMsg(data.message || "Pagamento não foi aprovado. Tente novamente.");
        return;
      }
    } catch (err: any) {
      console.error("Erro no pagamento:", err);
      const resp = (err as any)?.response;
      const data = resp?.data as { code?: string; message?: string } | undefined;

      if (data?.code === "PAYMENT_GATEWAY_ERROR") {
        setErrorMsg(data.message || "Erro ao comunicar com processador de cartão.");
      } else if (data?.code === "OUT_OF_STOCK") {
        setErrorMsg(data.message || "Alguns itens ficaram indisponíveis.");
      } else if (data?.message) {
        setErrorMsg(data.message);
      } else if (err instanceof Error) {
        setErrorMsg(err.message);
      } else {
        setErrorMsg("Não foi possível processar o pagamento.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto p-6">
      <h2 className="text-xl font-semibold mb-4 text-center">Pagamento com Cartão</h2>

      <CardPaymentWarnings
        timeLeft={timer.timeLeft}
        showWarning={timer.showWarning}
        showSecurityWarning={timer.showSecurityWarning}
        isExpired={timer.isExpired}
        formattedTime={timer.formattedTime}
        hasCheckoutResponse={!!checkoutResponse}
      />

      <OrderSummary subtotal={subtotal} shipping={shipping} discount={desconto} total={total} />

      <CouponDisplay discount={desconto} couponCode={couponCode} />

      <CardForm
        card={card}
        brand={brand}
        cvvLen={validation.cvvLen}
        onChangeNumber={onChangeNumber}
        onChangeHolder={onChangeHolder}
        onChangeMonth={onChangeMonth}
        onChangeYear={onChangeYear}
        onBlurYear={onBlurYear}
        onChangeCvv={onChangeCvv}
        onChangeBrand={onChangeBrand}
      />

      <InstallmentSelector
        installments={installmentsHook.installments}
        installmentOptions={installmentsHook.installmentOptions}
        perInstallment={installmentsHook.perInstallment}
        total={total}
        onChange={installmentsHook.setInstallments}
      />

      {!validation.isDocumentValid && (
        <div className="bg-yellow-50 text-yellow-700 p-2 mb-3 rounded">
          Informe um CPF válido no passo anterior para continuar.
        </div>
      )}

      {errorMsg && (
        <div className="bg-red-50 text-red-600 p-2 mb-4 rounded">{errorMsg}</div>
      )}

      <button
        disabled={!canPay}
        onClick={handlePay}
        className={`bg-blue-600 text-white py-2 w-full rounded ${canPay ? "hover:bg-blue-500" : "opacity-50 cursor-not-allowed"
          }`}
      >
        {loading ? "Processando..." : timer.isExpired ? "Pagamento Expirado" : "Pagar com Cartão"}
      </button>

      <ReviewPurchaseButton />
    </div>
  );
}
