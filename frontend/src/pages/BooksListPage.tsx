import { useMemo, useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import type { Book } from "../data/books";
import { books as localBooks } from "../data/books";
import { descriptions } from "../data/descriptions";
import { useCart } from "../hooks/useCart";
import { useStockByIds } from "../hooks/useStockByIds";
import { getAllBooks } from "../api/stock";
import BookPrice from "../components/common/BookPrice";
import VerificationStock from "../components/common/VerificationStock";

const BooksListPage = () => {
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const [quantity, setQuantity] = useState<Record<string, number>>({});
  const [books, setBooks] = useState<Book[]>(localBooks);
  const [loadingBooks, setLoadingBooks] = useState(true);
  const [openDescriptions, setOpenDescriptions] = useState<Record<string, boolean>>({});

  // Buscar livros da API ao carregar e mesclar com descrições do frontend
  useEffect(() => {
    const fetchBooks = async () => {
      setLoadingBooks(true);
      const apiBooks = await getAllBooks();
      if (apiBooks && apiBooks.length > 0) {
        // Mescla dados da API com descrições do frontend
        const mergedBooks = apiBooks.map((apiBook) => {
          // Busca descrição do frontend pelo ID
          const localDescription = descriptions[apiBook.id as keyof typeof descriptions];
          // Busca dados completos do livro local (para pegar relatedBooks, additionalInfo, etc)
          const localBook = localBooks.find((b) => b.id === apiBook.id);
          
          return {
            ...apiBook,
            // Descrição sempre do frontend (descriptions.ts)
            description: localDescription || apiBook.description || "",
            // Metadados do frontend se existirem
            relatedBooks: localBook?.relatedBooks || apiBook.relatedBooks || [],
            additionalInfo: localBook?.additionalInfo || apiBook.additionalInfo || {},
            author: localBook?.author || apiBook.author || "",
            category: localBook?.category || apiBook.category || "",
          };
        });
        setBooks(mergedBooks);
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

  const toggleDescription = (id: string) => {
    setOpenDescriptions((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const closeDescription = (id: string) => {
    setOpenDescriptions((prev) => ({ ...prev, [id]: false }));
  };

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

          const isDescriptionOpen = openDescriptions[book.id] || false;

          return (
            <div key={book.id} className="bg-background rounded-md shadow-md p-4 flex flex-col">
              <img src={book.imageUrl} alt={book.title} className="w-full max-w-xs rounded-md shadow-md mb-4 mx-auto" />
              <h2 className="text-2xl font-semibold text-primary mb-1">{book.title}</h2>
              
              <div className="mb-4">
                <p className="text-lg text-secondary mb-3">
                  <BookPrice price={book.price} />
                </p>

                {!loading && !isAvailable ? (
                  <p className="mt-1 text-gray-600 font-semibold mb-4">Este livro foi esgotado, esperando nova edição a ser publicada.</p>
                ) : !loading && lowStock ? (
                  <p className="mt-1 text-red-600 font-bold mb-4">({s}) EM ESTOQUE</p>
                ) : null}

                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-2">
                    <button className="px-4 py-2 bg-gray-200 rounded-md hover:bg-gray-300 transition" onClick={() => handleDecrease(book.id)} disabled={loading || !isAvailable}>-</button>
                    <span className="text-lg min-w-[2rem] text-center">{q}</span>
                    <button className="px-4 py-2 bg-gray-200 rounded-md hover:bg-gray-300 transition" onClick={() => handleIncrease(book.id, realStock)} disabled={loading || !isAvailable}>+</button>
                  </div>
                  {loading ? (
                    <VerificationStock />
                  ) : isAvailable ? (
                    <button
                      onClick={() => handleAddToCart(book)}
                      className="px-6 py-2 rounded-md shadow-md transition bg-green-600 text-white hover:bg-green-700"
                    >
                      Adicionar ao Carrinho
                    </button>
                  ) : (
                    <span className="px-6 py-2 bg-gray-300 text-gray-800 rounded-md shadow-md">Esgotado</span>
                  )}
                </div>
              </div>

              <div className="flex justify-start mb-3">
                <button
                  onClick={() => toggleDescription(book.id)}
                  className="px-6 py-2 bg-blue-600 text-white rounded-md shadow-md hover:bg-blue-700 transition"
                >
                  {isDescriptionOpen ? "Ocultar sinopse" : "Ler sinopse"}
                </button>
              </div>

              {isDescriptionOpen && (
                <>
                  <div className="text-sm text-text-secondary mb-3">
                    <span dangerouslySetInnerHTML={{ __html: book.description }} />
                  </div>
                  <div className="flex justify-start">
                    <button
                      onClick={() => closeDescription(book.id)}
                      className="px-6 py-2 bg-gray-600 text-white rounded-md shadow-md hover:bg-gray-700 transition"
                    >
                      Fechar descrição
                    </button>
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default BooksListPage;
