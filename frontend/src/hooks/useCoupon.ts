// src/hooks/useCoupon.ts
import { useState, useEffect, useCallback } from 'react';
import { apiGet, apiPost } from '../api/http';

interface CouponState {
  code: string;
  discount: number;
  isValid: boolean;
  errorMessage?: string;
}

const COUPON_STORAGE_KEY = 'applied_coupon';

export const useCoupon = () => {
  const [couponState, setCouponState] = useState<CouponState>({
    code: '',
    discount: 0,
    isValid: false,
  });
  const [inputValue, setInputValue] = useState('');
  const [isValidating, setIsValidating] = useState(false);

  // Carregar cupom do sessionStorage na inicialização
  useEffect(() => {
    const loadCoupon = () => {
      const storedCoupon = sessionStorage.getItem(COUPON_STORAGE_KEY);
      if (storedCoupon) {
        try {
          const parsed = JSON.parse(storedCoupon);
          setCouponState(parsed);
          setInputValue(parsed.code);
        } catch {
          sessionStorage.removeItem(COUPON_STORAGE_KEY);
        }
      }
    };

    loadCoupon();

    const handleCouponChange = () => loadCoupon();
    const handleFocus = () => loadCoupon();

    window.addEventListener('couponChanged', handleCouponChange);
    window.addEventListener('focus', handleFocus);

    return () => {
      window.removeEventListener('couponChanged', handleCouponChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, []);

  /**
   * Normaliza código de cupom removendo acentos e convertendo para maiúsculas
   * Aceita: "lancamento", "LANCAMENTO", "lançamento", "LANÇAMENTO" -> todos viram "LANCAMENTO"
   */
  const normalizeCouponCode = useCallback((code: string): string => {
    return code
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase()
      .trim();
  }, []);

  const applyCouponLocal = useCallback((code: string): { success: boolean; discountAmount?: number } => {
    const normalizedInput = normalizeCouponCode(code);
    
    // Verificar cupom genérico (BONUS)
    const couponCode = import.meta.env.VITE_COUPON_CODE;
    const discountValue = import.meta.env.VITE_COUPON_DISCOUNT_VALUE;
    
    // Verificar cupom de lançamento
    const lancamentoCode = import.meta.env.VITE_COUPON_LANCAMENTO_CODE;
    const lancamentoDiscountValue = import.meta.env.VITE_COUPON_LANCAMENTO_DISCOUNT_VALUE;
    
    let validCoupon: string | null = null;
    let fixedDiscount: number | null = null;
    
    // Verificar se é o cupom genérico
    if (couponCode && discountValue) {
      const normalizedGeneric = normalizeCouponCode(couponCode);
      if (normalizedInput === normalizedGeneric) {
        validCoupon = normalizedGeneric;
        fixedDiscount = Number(discountValue);
      }
    }
    
    // Verificar se é o cupom de lançamento
    if (!validCoupon && lancamentoCode && lancamentoDiscountValue) {
      const normalizedLancamento = normalizeCouponCode(lancamentoCode);
      if (normalizedInput === normalizedLancamento) {
        validCoupon = normalizedLancamento;
        fixedDiscount = Number(lancamentoDiscountValue);
      }
    }
    
    if (!validCoupon || fixedDiscount === null) {
      alert("Cupom inválido.");
      return { success: false };
    }

    if (isNaN(fixedDiscount) || fixedDiscount <= 0) {
      alert("Configuração de desconto inválida. Entre em contato com o suporte.");
      return { success: false };
    }

    const newState = {
      code: validCoupon,
      discount: fixedDiscount,
      isValid: true,
    };
    
    setCouponState(newState);
    setInputValue(validCoupon);
    sessionStorage.setItem(COUPON_STORAGE_KEY, JSON.stringify(newState));
    
    window.dispatchEvent(new CustomEvent('couponChanged'));
    return { success: true, discountAmount: fixedDiscount };
  }, [normalizeCouponCode]);

  const applyCoupon = useCallback(async (
    code: string, 
    orderTotal: number, 
    cartItems?: Array<{ id: string; title: string; price: number; quantity: number; imageUrl: string }>
  ): Promise<{ success: boolean; discountAmount?: number }> => {
    if (!code.trim()) {
      alert("Digite um código de cupom.");
      return { success: false };
    }

    setIsValidating(true);

    try {
      const result = await apiPost<{
        valid: boolean;
        discountAmount?: number;
        errorMessage?: string;
      }>("/coupons/validate", {
        code: code.trim(),
        orderTotal: orderTotal,
        userEmail: null,
        cartItems: cartItems || null
      });

      if (result.valid) {
        // Garantir que discountAmount seja sempre um número
        const discountValue = result.discountAmount ?? 0;
        const newState: CouponState = {
          code: code.trim().toUpperCase(),
          discount: discountValue,
          isValid: true,
        };
        
        setCouponState(newState);
        setInputValue(code.trim().toUpperCase());
        sessionStorage.setItem(COUPON_STORAGE_KEY, JSON.stringify(newState));
        
        window.dispatchEvent(new CustomEvent('couponChanged'));
        return { success: true, discountAmount: result.discountAmount };
      } else {
        alert(result.errorMessage || "Cupom inválido.");
        return { success: false };
      }
    } catch (error) {
      console.error('Erro ao validar cupom via API:', error);
      
      // Fallback para sistema local com variáveis de ambiente
      return applyCouponLocal(code);
    } finally {
      setIsValidating(false);
    }
  }, [applyCouponLocal]);



  const clearCoupon = useCallback(() => {
    setCouponState({ code: '', discount: 0, isValid: false });
    setInputValue('');
    sessionStorage.removeItem(COUPON_STORAGE_KEY);
    window.dispatchEvent(new CustomEvent('couponChanged'));
  }, []);

  /**
   * Busca informações de um cupom sem validar (opcional)
   * Útil para mostrar informações do cupom antes de aplicar
   */
  const getCouponInfo = useCallback(async (code: string): Promise<{
    exists: boolean;
    discountAmount?: number;
    description?: string;
  } | null> => {
    if (!code.trim()) return null;

    try {
      const result = await apiGet<{
        exists: boolean;
        discountAmount?: number;
        description?: string;
      }>(`/coupons/${encodeURIComponent(code.trim())}`);
      return result;
    } catch {
      return null;
    }
  }, []);

  const getDiscountAmount = useCallback((subtotal: number): number => {
    if (!couponState.isValid) return 0;
    
    // Limitar o desconto a um valor fixo máximo (R$ 15.00)
    const MAX_DISCOUNT = 15.00;
    const actualDiscount = Math.min(couponState.discount, MAX_DISCOUNT);
    
    // Garantir que o desconto não seja maior que o subtotal
    // E que sempre seja pelo menos 0.01 para evitar erro na Efí
    const finalDiscount = Math.min(actualDiscount, subtotal);
    return Math.max(finalDiscount, 0);
  }, [couponState.isValid, couponState.discount]);

  return {
    couponCode: couponState.code,
    discount: couponState.discount,
    isValid: couponState.isValid,
    inputValue,
    setInputValue,
    applyCoupon,
    getCouponInfo,
    clearCoupon,
    getDiscountAmount,
    isValidating,
    errorMessage: couponState.errorMessage,
  };
};