import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { Book } from "../../data/books";
import BookDescription from "./BookDescription";
import BookAuthor from "./BookAuthor";
import AdditionalInfo from "./AdditionalInfo";
import RelatedBooks from "./RelatedBooks";
import AuthorInfo from "./AuthorInfo";
import ButtonCountCart from "../cart/ButtonCountCart";
import { useCart } from "../../hooks/useCart";
import BookPrice from "../common/BookPrice";

type BookDetailsProps = Book;

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

  return (
    <div className="container mx-auto my-16 px-4 ">
      <div className="flex flex-col md:flex-row items-start gap-16">
        <div className="w-full md:w-1/3 flex justify-center">
          <img src={imageUrl} alt={title} className="w-full max-w-xs rounded-md shadow-md" />
        </div>

        <div className="flex-1">
          <h1 className="text-4xl font-bold text-primary mb-4">{title}</h1>

          <div className="flex items-center gap-3 mb-2">
            <p className="text-3xl text-secondary font-semibold">
              <BookPrice price={price} />
            </p>
            {!isAvailable ? (
              <span className="px-3 py-1 text-sm bg-gray-300 text-gray-800 rounded">Esgotado</span>
            ) : lowStock ? (
              <p className="mt-1 text-red-600 font-bold">
                ({stock}) EM ESTOQUE
              </p>
            ) : null}
          </div>

          <BookDescription description={description} />
          <BookAuthor author={author} />

          <div className="flex items-center gap-4 mb-8">
            <ButtonCountCart quantity={quantity} onDecrease={handleDecrease} onIncrease={handleIncrease} />
            <button
              onClick={handleAddToCart}
              disabled={!isAvailable}
              className={`px-6 py-2 rounded-md shadow-md transition ${
                isAvailable ? "bg-green-600 text-white hover:bg-green-700" : "bg-gray-300 text-gray-600 cursor-not-allowed"
              }`}
            >
              {isAvailable ? "Adicionar ao Carrinho" : "Esgotado"}
            </button>
          </div>
        </div>
      </div>

      <AdditionalInfo additionalInfo={additionalInfo} />
      <RelatedBooks relatedBooks={relatedBooks} />
      <AuthorInfo author={author} />
    </div>
  );
};

export default BookDetails;
