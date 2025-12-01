import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { Book } from "../../data/booksWithRelated";
import { books as allBooks } from "../../data/booksWithRelated";
import BookDescription from "./BookDescription";
import BookAuthor from "./BookAuthor";
import AdditionalInfo from "./AdditionalInfo";
import RelatedBooks from "./RelatedBooks";
import AuthorInfo from "./AuthorInfo";
import ButtonCountCart from "../cart/ButtonCountCart";
import { useCart } from "../../hooks/useCart";
import BookPrice from "../common/BookPrice";
import VerificationStock from "../common/VerificationStock";

type BookDetailsProps = Book & { loading?: boolean };

const BookDetails = ({
  id,
  title,
  imageUrl,
  price,
  description,
  additionalInfo,
  author,
  relatedBooks,
  category,
  stock,
  loading = false,
}: BookDetailsProps) => {
  const [quantity, setQuantity] = useState(1);
  const navigate = useNavigate();
  const { addToCart } = useCart();

  const isAvailable = (stock ?? 0) > 0;
  const lowStock = isAvailable && (stock ?? 0) <= 10;

  useEffect(() => {
    if (!isAvailable) setQuantity(1);
    else if (quantity > (stock ?? 1)) setQuantity(stock ?? 1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stock, isAvailable]);

  const handleIncrease = () => {
    if (!isAvailable) return;
    setQuantity((prev) => Math.min(prev + 1, stock ?? 1));
  };

  const handleDecrease = () => setQuantity((prev) => Math.max(1, prev - 1));

  const handleAddToCart = () => {
    if (!isAvailable) {
      alert("Este produto está esgotado.");
      return;
    }
    addToCart(
      { id, title, imageUrl, price, description, author, additionalInfo, category, relatedBooks, stock },
      quantity
    );
    alert("Item adicionado ao carrinho!");
    navigate("/cart");
  };

  // Se relatedBooks vier vazio/undefined, calcula aqui com base no autor
  const effectiveRelatedBooks =
    relatedBooks && relatedBooks.length > 0
      ? relatedBooks
      : (() => {
        const sameAuthorBooks = allBooks.filter(
          (b) => b.author === author && b.id !== id
        );

        const totalSameAuthor = sameAuthorBooks.length;
        if (totalSameAuthor === 0) return [];

        // 2 livros → 1 relacionado
        // 3 livros → 2 relacionados
        // 4 livros → 3 relacionados
        // 5+       → 3 relacionados aleatórios
        const maxCount = Math.min(totalSameAuthor, 3);

        const shuffled = [...sameAuthorBooks];
        for (let i = shuffled.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }

        return shuffled.slice(0, maxCount).map((b) => ({
          id: b.id,
          title: b.title,
          imageUrl: b.imageUrl,
          price: b.price,
          category: b.category,
        }));
      })();

  return (
    <div className="container mx-auto my-16 px-4 ">
      <div className="flex flex-col md:flex-row items-start gap-16">
        <div className="w-full md:w-1/3 flex justify-center">
          <img src={imageUrl} alt={title} className="w-full max-w-xs rounded-md shadow-md" />
        </div>

        <div className="flex-1">
          <h1 className="text-4xl font-bold text-primary mb-4">{title}</h1>

          <div className="mb-4">
            <p className="text-3xl text-secondary font-semibold mb-3">
              <BookPrice price={price} />
            </p>

            {!isAvailable ? (
              <p className="mt-1 text-gray-600 font-semibold mb-4">Este livro foi esgotado, esperando nova edição a ser publicada.</p>
            ) : lowStock ? (
              <p className="mt-1 text-red-600 font-bold mb-4">({stock}) EM ESTOQUE</p>
            ) : null}

            <div className="flex items-center gap-4">
              <ButtonCountCart quantity={quantity} onDecrease={handleDecrease} onIncrease={handleIncrease} />
              {loading ? (
                <VerificationStock />
              ) : isAvailable ? (
                <button
                  onClick={handleAddToCart}
                  className="px-6 py-2 rounded-md shadow-md transition bg-green-600 text-white hover:bg-green-700"
                >
                  Adicionar ao Carrinho
                </button>
              ) : (
                <span className="px-6 py-2 bg-gray-300 text-gray-800 rounded-md shadow-md">Esgotado</span>
              )}
            </div>
          </div>

          <BookDescription description={description} />
          <BookAuthor author={author} />
        </div>
      </div>

      <AdditionalInfo additionalInfo={additionalInfo} />
      <RelatedBooks relatedBooks={effectiveRelatedBooks} />
      <AuthorInfo author={author} />
    </div>
  );
};

export default BookDetails;
