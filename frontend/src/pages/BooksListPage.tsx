import { useMemo, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import type { Book } from "../data/books";
import { books as localBooks } from "../data/books";
import { useCart } from "../hooks/useCart";
import { useStockByIds } from "../hooks/useStockByIds";
import { getAllBooks } from "../api/stock";
import BookPrice from "../components/common/BookPrice";

const BooksListPage = () => {
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const [quantity, setQuantity] = useState<Record<string, number>>({});
  const [books, setBooks] = useState<Book[]>(localBooks);
  const [loadingBooks, setLoadingBooks] = useState(true);

  // Buscar livros da API ao carregar
  useEffect(() => {
    const fetchBooks = async () => {
      setLoadingBooks(true);
      const apiBooks = await getAllBooks();
      if (apiBooks && apiBooks.length > 0) {
        setBooks(apiBooks);
      } else {
        // Fallback para dados locais se API falhar
        setBooks(localBooks);
      }
      setLoadingBooks(false);
    };
    fetchBooks();
  }, []);

  const ids = useMemo(() => books.map(b => b.id), [books]);
  const { data: stockMap, loading: loadingStock } = useStockByIds(ids);
  const loading = loadingBooks || loadingStock;

  const handleAddToCart = (book: Book) => {
    const real = stockMap[book.id]?.stock;
    const realStock = typeof real === "number" ? real : 0; // bloqueia até confirmar
    if (realStock <= 0) {
      alert("Este produto está esgotado.");
      return;
    }
    const q = Math.min(quantity[book.id] || 1, realStock);
    addToCart({ ...book, stock: realStock }, q);
    alert("Item adicionado ao carrinho!");
    navigate("/cart");
  };

  const handleIncrease = (id: string, max: number) =>
    setQuantity((prev) => ({ ...prev, [id]: Math.min((prev[id] || 1) + 1, max) }));
  const handleDecrease = (id: string) =>
    setQuantity((prev) => ({ ...prev, [id]: Math.max((prev[id] || 1) - 1, 1) }));

  return (
    <div className="container mx-auto mt-2 mb-8 px-4">
      <h1 className="text-4xl font-bold text-primary mb-8">Livros</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        {books.map((book) => {
          const s = stockMap[book.id]?.stock;
          const realStock = typeof s === "number" ? s : 0;
          const isAvailable = realStock > 0;
          const lowStock = isAvailable && realStock <= 10;
          const q = quantity[book.id] || 1;

          return (
            <div key={book.id} className="bg-background rounded-md shadow-md p-4">
              <img src={book.imageUrl} alt={book.title} className="w-full max-w-xs rounded-md shadow-md mb-4" />
              <h2 className="text-2xl font-semibold text-primary mb-1">{book.title}</h2>
              <p className="text-lg text-secondary mb-2">
                <BookPrice price={book.price} />
              </p>

              <p className="text-sm text-text-secondary mb-3">
                <span dangerouslySetInnerHTML={{ __html: book.description }} />
              </p>

              {loading ? (
                <span className="inline-block mb-3 px-3 py-1 text-sm bg-gray-200 text-gray-700 rounded">
                  Verificando estoque…
                </span>
              ) : !isAvailable ? (
                <span className="inline-block mb-3 px-3 py-1 text-sm bg-gray-300 text-gray-800 rounded">Esgotado</span>
              ) : lowStock ? (
                <p className="mt-1 text-red-600 font-bold mb-4">({s}) EM ESTOQUE</p>
              ) : null}

              <div className="flex items-center gap-4 mb-4">
                <button className="px-4 py-2 bg-gray-200" onClick={() => handleDecrease(book.id)} disabled={!isAvailable}>-</button>
                <span className="text-lg">{q}</span>
                <button className="px-4 py-2 bg-gray-200" onClick={() => handleIncrease(book.id, realStock)} disabled={!isAvailable}>+</button>
                <button
                  onClick={() => handleAddToCart(book)}
                  disabled={!isAvailable}
                  className={`px-6 py-2 rounded-md shadow-md transition ${
                    isAvailable ? "bg-green-600 text-white hover:bg-green-700" : "bg-gray-300 text-gray-600 cursor-not-allowed"
                  }`}
                >
                  {isAvailable ? "Adicionar ao Carrinho" : "Esgotado"}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default BooksListPage;
