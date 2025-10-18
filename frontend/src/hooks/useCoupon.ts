// src/hooks/useCoupon.ts
import { useState, useEffect } from 'react';

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

  const applyCoupon = async (code: string, orderTotal: number): Promise<boolean> => {
    if (!code.trim()) {
      alert("Digite um código de cupom.");
      return false;
    }

    setIsValidating(true);

    try {
      const API_BASE = import.meta.env.VITE_API_BASE;
      if (!API_BASE) {
        throw new Error("VITE_API_BASE não configurado");
      }

      const response = await fetch(`${API_BASE}/api/coupons/validate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          code: code.trim(),
          orderTotal: orderTotal,
          userEmail: null // ou pegar do contexto do usuário
        }),
      });

      const result = await response.json();

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
        return true;
      } else {
        alert(result.errorMessage || "Cupom inválido.");
        return false;
      }
    } catch (error) {
      console.error('Erro ao validar cupom:', error);
      alert("Erro ao validar cupom. Tente novamente.");
      return false;
    } finally {
      setIsValidating(false);
    }
  };

  const clearCoupon = () => {
    setCouponState({ code: '', discount: 0, isValid: false });
    setInputValue('');
    sessionStorage.removeItem(COUPON_STORAGE_KEY);
    window.dispatchEvent(new CustomEvent('couponChanged'));
  };

  const getDiscountAmount = (subtotal: number): number => {
    if (!couponState.isValid) return 0;
    return Math.min(couponState.discount, subtotal);
  };

  return {
    couponCode: couponState.code,
    discount: couponState.discount,
    isValid: couponState.isValid,
    inputValue,
    setInputValue,
    applyCoupon,
    clearCoupon,
    getDiscountAmount,
    isValidating,
    errorMessage: couponState.errorMessage,
  };
};