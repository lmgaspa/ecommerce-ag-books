import { useNavigate } from "react-router-dom";

interface PixQRCodeDisplayProps {
  qrCodeImg: string;
  pixCopiaECola: string;
  isExpired: boolean;
  formattedTime: string;
  expiresAtMs: number | null;
}

/**
 * Componente para exibir QR Code Pix e opção de copiar e colar
 */
export const PixQRCodeDisplay = ({
  qrCodeImg,
  pixCopiaECola,
  isExpired,
  formattedTime,
  expiresAtMs,
}: PixQRCodeDisplayProps) => {
  const navigate = useNavigate();

  if (!qrCodeImg) return null;

  return (
    <div className="mt-10 text-center space-y-3">
      {!isExpired ? (
        <>
          <p className="text-lg font-medium">Escaneie o QR Code com seu app do banco:</p>
          <img src={qrCodeImg} alt="QR Code Pix" className="mx-auto" />
          {pixCopiaECola && (
            <div className="max-w-xl mx-auto">
              <p className="mt-4 text-sm text-gray-700">Ou copie e cole no seu app:</p>
              <div className="flex gap-2 items-center mt-1">
                <input
                  readOnly
                  value={pixCopiaECola}
                  className="flex-1 border rounded px-2 py-1 text-xs"
                />
                <button
                  onClick={() => navigator.clipboard.writeText(pixCopiaECola)}
                  className="bg-black text-white px-3 py-1 rounded text-sm"
                >
                  Copiar
                </button>
              </div>
            </div>
          )}
          {expiresAtMs && (
            <p className="text-sm text-gray-600 mt-2">
              Este QR expira em <span className="font-semibold">{formattedTime}</span>.
            </p>
          )}
        </>
      ) : (
        <div className="p-4 border rounded bg-red-50 text-red-800 inline-block">
          <p className="font-medium">PIX invalidado por questões de segurança</p>
          <p className="text-sm">
            O PIX foi invalidado por questões de segurança aos 10 segundos restantes. Gere um novo
            pedido para tentar novamente.
          </p>
          <button
            className="mt-3 bg-black text-white px-4 py-2 rounded"
            onClick={() => navigate("/checkout")}
          >
            Voltar ao checkout
          </button>
        </div>
      )}
    </div>
  );
};

