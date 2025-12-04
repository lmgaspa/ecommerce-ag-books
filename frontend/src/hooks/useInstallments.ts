import { useState, useEffect, useMemo } from "react";
import { getInstallments, type CardBrand, type InstallmentItem } from "../services/efiCard";

interface UseInstallmentsProps {
  total: number;
  brand: CardBrand;
  payeeCode: string;
  efiEnv: "production" | "sandbox";
}

/**
 * Hook para buscar e gerenciar parcelas de cartão via EFI
 */
export const useInstallments = ({ total, brand, payeeCode, efiEnv }: UseInstallmentsProps) => {
  const [installments, setInstallments] = useState<number>(1);
  const [installmentOptions, setInstallmentOptions] = useState<InstallmentItem[]>([]);
  const [loading, setLoading] = useState(false);

  // Buscar parcelas da API da EFI
  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const cents = Math.round(total * 100);
        if (cents <= 0) {
          setInstallmentOptions([]);
          setInstallments(1);
          return;
        }

        const resp = await getInstallments(payeeCode, efiEnv, brand, cents);
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
      } finally {
        setLoading(false);
      }
    })();
  }, [brand, total, payeeCode, efiEnv]);

  // Encontrar parcela selecionada
  const selectedInstallment = useMemo(
    () => installmentOptions.find((opt) => opt.installment === installments),
    [installmentOptions, installments]
  );

  // Calcular valor por parcela
  const perInstallment = useMemo(() => {
    if (installments <= 1) return total;
    
    // Se temos uma opção selecionada da API, mas o total mudou, recalcula baseado no total atual
    if (selectedInstallment) {
      const apiTotal = (selectedInstallment.value / 100) * selectedInstallment.installment;
      if (Math.abs(apiTotal - total) > 0.01) {
        return Math.round((total / installments) * 100) / 100;
      }
      return selectedInstallment.value / 100;
    }
    
    return Math.round((total / installments) * 100) / 100;
  }, [selectedInstallment, installments, total]);

  return {
    installments,
    setInstallments,
    installmentOptions,
    selectedInstallment,
    perInstallment,
    loading,
  };
};
