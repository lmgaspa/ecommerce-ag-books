import React from "react";
import type { CardBrand } from "../../../services/efiCard";

interface CardFormProps {
    card: {
        number: string;
        holderName: string;
        expirationMonth: string;
        expirationYear: string;
        cvv: string;
    };
    brand: CardBrand;
    cvvLen: number;
    onChangeNumber: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onChangeHolder: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onChangeMonth: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onChangeYear: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onBlurYear: () => void;
    onChangeCvv: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onChangeBrand: (e: React.ChangeEvent<HTMLSelectElement>) => void;
}

/**
 * Componente de formulário de cartão de crédito
 */
export const CardForm: React.FC<CardFormProps> = ({
    card,
    brand,
    cvvLen,
    onChangeNumber,
    onChangeHolder,
    onChangeMonth,
    onChangeYear,
    onBlurYear,
    onChangeCvv,
    onChangeBrand,
}) => {
    return (
        <>
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
        </>
    );
};
