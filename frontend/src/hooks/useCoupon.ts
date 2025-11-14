// src/hooks/useCoupon.ts
import { useState, useEffect } from 'react';
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

  const applyCoupon = async (code: string, orderTotal: number): Promise<{ success: boolean; discountAmount?: number }> => {
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
        userEmail: null
      });

      if (result.valid) {
        const newState = {
          code: code.trim().toUpperCase(),
          discount: result.discountAmount,
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
  };

  const applyCouponLocal = (code: string): { success: boolean; discountAmount?: number } => {
    // Verificar se as variáveis de ambiente estão configuradas
    const couponCode = import.meta.env.VITE_COUPON_CODE;
    const discountValue = import.meta.env.VITE_COUPON_DISCOUNT_VALUE;
    
    if (!couponCode || !discountValue) {
      alert("Sistema de cupons não configurado. Entre em contato com o suporte.");
      return { success: false };
    }

    const validCoupon = couponCode.toUpperCase();
    const FIXED_DISCOUNT = Number(discountValue);

    if (isNaN(FIXED_DISCOUNT) || FIXED_DISCOUNT <= 0) {
      alert("Configuração de desconto inválida. Entre em contato com o suporte.");
      return { success: false };
    }

    if (code.trim().toUpperCase() === validCoupon) {
      const newState = {
        code: code.trim().toUpperCase(),
        discount: FIXED_DISCOUNT,
        isValid: true,
      };
      
      setCouponState(newState);
      setInputValue(code.trim().toUpperCase());
      sessionStorage.setItem(COUPON_STORAGE_KEY, JSON.stringify(newState));
      
      window.dispatchEvent(new CustomEvent('couponChanged'));
      return { success: true, discountAmount: FIXED_DISCOUNT };
    } else {
      alert("Cupom inválido.");
      return { success: false };
    }
  };

  const clearCoupon = () => {
    setCouponState({ code: '', discount: 0, isValid: false });
    setInputValue('');
    sessionStorage.removeItem(COUPON_STORAGE_KEY);
    window.dispatchEvent(new CustomEvent('couponChanged'));
  };

  /**
   * Busca informações de um cupom sem validar (opcional)
   * Útil para mostrar informações do cupom antes de aplicar
   */
  const getCouponInfo = async (code: string): Promise<{
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
  };

  const getDiscountAmount = (subtotal: number): number => {
    if (!couponState.isValid) return 0;
    
    // Limitar o desconto a um valor fixo máximo (R$ 15.00)
    const MAX_DISCOUNT = 15.00;
    const actualDiscount = Math.min(couponState.discount, MAX_DISCOUNT);
    
    // Garantir que o desconto não seja maior que o subtotal
    // E que sempre seja pelo menos 0.01 para evitar erro na Efí
    const finalDiscount = Math.min(actualDiscount, subtotal);
    return Math.max(finalDiscount, 0);
  };

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