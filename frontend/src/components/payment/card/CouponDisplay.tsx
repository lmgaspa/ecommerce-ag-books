import React from "react";

interface CouponDisplayProps {
    discount: number;
    couponCode: string | null;
}

/**
 * Componente para exibir informações do cupom aplicado
 */
export const CouponDisplay: React.FC<CouponDisplayProps> = ({
    discount,
    couponCode,
}) => {
    if (discount > 0) {
        return (
            <div className="bg-green-50 border-2 border-green-500 rounded-lg p-4 mb-4 animate-pulse">
                <div className="flex items-center gap-2">
                    <span className="text-2xl">✅</span>
                    <div>
                        <p className="font-bold text-green-800">
                            {couponCode ? `Cupom Aplicado: ${couponCode}` : "Desconto Aplicado"}
                        </p>
                        <p className="text-sm text-green-700">
                            Desconto de R$ {discount.toFixed(2).replace(".", ",")} aplicado com sucesso!
                        </p>
                    </div>
                </div>
            </div>
        );
    }

    if (couponCode) {
        return (
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
        );
    }

    return null;
};
