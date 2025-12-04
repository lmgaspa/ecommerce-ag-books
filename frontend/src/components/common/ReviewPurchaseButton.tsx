import { useNavigate } from "react-router-dom";

interface ReviewPurchaseButtonProps {
  className?: string;
}

/**
 * Componente reutilizável para o botão "Revisar compra"
 * Usado em páginas de pagamento (Pix e Cartão)
 */
export default function ReviewPurchaseButton({ className = "" }: ReviewPurchaseButtonProps) {
  const navigate = useNavigate();

  const handleReviewClick = () => {
    navigate("/checkout");
  };

  return (
    <div className={`mt-8 flex justify-between ${className}`}>
      <button
        onClick={handleReviewClick}
        className="bg-gray-300 hover:bg-gray-400 text-black px-4 py-2 rounded"
      >
        Revisar compra
      </button>
      <span className="text-sm text-gray-500 self-center" />
    </div>
  );
}
