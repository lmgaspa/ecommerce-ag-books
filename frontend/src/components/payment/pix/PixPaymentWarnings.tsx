interface PixPaymentWarningsProps {
  remainingSec: number;
  warningAt?: number;
  securityWarningAt?: number;
}

/**
 * Componente para exibir avisos de segurança do Pix
 */
export const PixPaymentWarnings = ({
  remainingSec,
  warningAt,
  securityWarningAt,
}: PixPaymentWarningsProps) => {
  let warning: string | null = null;
  let securityWarning: string | null = null;

  if (securityWarningAt && remainingSec <= securityWarningAt) {
    securityWarning = "🔒 Por questões de segurança, o PIX foi invalidado!";
  } else if (warningAt && remainingSec <= warningAt) {
    warning = "⚠️ PIX será invalidado em 10 segundos! Pague agora!";
  }

  if (securityWarning) {
    return (
      <div className="bg-red-100 text-red-800 p-3 rounded border border-red-300 font-bold mb-4">
        {securityWarning}
      </div>
    );
  }

  if (warning) {
    return (
      <div className="bg-orange-50 text-orange-700 p-3 rounded border border-orange-200 mb-4">
        {warning}
      </div>
    );
  }

  return null;
};

