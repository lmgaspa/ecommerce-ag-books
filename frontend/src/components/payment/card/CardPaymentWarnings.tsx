import React from "react";

interface CardPaymentWarningsProps {
    timeLeft: number | null;
    showWarning: boolean;
    showSecurityWarning: boolean;
    isExpired: boolean;
    formattedTime: string;
    hasCheckoutResponse: boolean;
}

/**
 * Componente para exibir avisos de tempo de expiração do pagamento
 */
export const CardPaymentWarnings: React.FC<CardPaymentWarningsProps> = ({
    timeLeft,
    showWarning,
    showSecurityWarning,
    isExpired,
    formattedTime,
    hasCheckoutResponse,
}) => {
    if (!hasCheckoutResponse) return null;

    if (isExpired) {
        return (
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
        );
    }

    if (showSecurityWarning) {
        return (
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
        );
    }

    if (showWarning) {
        return (
            <div className="bg-orange-50 text-orange-700 p-3 mb-4 rounded-lg border border-orange-200">
                <div className="flex items-center">
                    <span className="text-lg mr-2">⚠️</span>
                    <div>
                        <p className="font-medium">
                            Cartão será invalidado em {formattedTime}!
                        </p>
                        <p className="text-sm">Complete o pagamento agora</p>
                    </div>
                </div>
            </div>
        );
    }

    return (
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
    );
};
