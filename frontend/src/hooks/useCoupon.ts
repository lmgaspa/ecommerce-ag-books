// src/hooks/useCoupon.ts
import { useState, useEffect } from 'react';

interface CouponState {
  code: string;
  discount: number;
  isValid: boolean;
}

const COUPON_STORAGE_KEY = 'applied_coupon';

export const useCoupon = () => {
  const [couponState, setCouponState] = useState<CouponState>({
    code: '',
    discount: 0,
    isValid: false,
  });
  const [inputValue, setInputValue] = useState('');

  // Carregar cupom do sessionStorage na inicialização
  useEffect(() => {
    const loadCoupon = () => {
      const storedCoupon = sessionStorage.getItem(COUPON_STORAGE_KEY);
      if (storedCoupon) {
        try {
          const parsed = JSON.parse(storedCoupon);
          setCouponState(parsed);
          setInputValue(parsed.code); // Preencher o input com o código aplicado
        } catch {
          // Se não conseguir fazer parse, limpa o storage
          sessionStorage.removeItem(COUPON_STORAGE_KEY);
        }
      }
    };

    // Carregar na inicialização
    loadCoupon();

    // Listener para evento customizado de mudança de cupom
    const handleCouponChange = () => {
      loadCoupon();
    };

    // Listener para quando a página ganha foco (volta de outra página)
    const handleFocus = () => {
      loadCoupon();
    };

    window.addEventListener('couponChanged', handleCouponChange);
    window.addEventListener('focus', handleFocus);

    return () => {
      window.removeEventListener('couponChanged', handleCouponChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, []);

  const applyCoupon = (code: string): boolean => {
    // Verificar se as variáveis de ambiente estão configuradas
    const couponCode = import.meta.env.VITE_COUPON_CODE;
    const discountValue = import.meta.env.VITE_COUPON_DISCOUNT_VALUE;
    
    if (!couponCode || !discountValue) {
      alert("Sistema de cupons não configurado. Entre em contato com o suporte.");
      return false;
    }

    const validCoupon = couponCode.toUpperCase();
    const FIXED_DISCOUNT = Number(discountValue);

    if (isNaN(FIXED_DISCOUNT) || FIXED_DISCOUNT <= 0) {
      alert("Configuração de desconto inválida. Entre em contato com o suporte.");
      return false;
    }

    if (code.trim().toUpperCase() === validCoupon) {
      const newState = {
        code: code.trim().toUpperCase(),
        discount: FIXED_DISCOUNT,
        isValid: true,
      };
      
      setCouponState(newState);
      setInputValue(code.trim().toUpperCase()); // Atualizar o input também
      sessionStorage.setItem(COUPON_STORAGE_KEY, JSON.stringify(newState));
      
      // Disparar evento customizado para notificar outras páginas
      window.dispatchEvent(new CustomEvent('couponChanged'));
      
      return true;
    } else {
      alert("Cupom inválido.");
      return false;
    }
  };

  const clearCoupon = () => {
    setCouponState({ code: '', discount: 0, isValid: false });
    setInputValue(''); // Limpar o input também
    sessionStorage.removeItem(COUPON_STORAGE_KEY);
    
    // Disparar evento customizado para notificar outras páginas
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
  };
};
