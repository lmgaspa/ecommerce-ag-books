import React from "react";
import type { InstallmentItem } from "../../../services/efiCard";

interface InstallmentSelectorProps {
    installments: number;
    installmentOptions: InstallmentItem[];
    perInstallment: number;
    total: number;
    onChange: (value: number) => void;
}

/**
 * Componente seletor de parcelas
 */
export const InstallmentSelector: React.FC<InstallmentSelectorProps> = ({
    installments,
    installmentOptions,
    perInstallment,
    total,
    onChange,
}) => {
    return (
        <>
            <label className="block text-sm font-medium mb-1">Parcelas</label>
            <select
                className="border p-2 w-full rounded mb-2"
                value={installments}
                onChange={(e) => onChange(Number(e.target.value))}
            >
                {installmentOptions.length > 0
                    ? installmentOptions.map((opt) => {
                        // Recalcula o valor por parcela baseado no total atual (com desconto)
                        const apiTotal = (opt.value / 100) * opt.installment;
                        const adjustedValue =
                            Math.abs(apiTotal - total) > 0.01
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
        </>
    );
};
