import React from "react";
import { useNavigate } from "react-router-dom";
import type { Book } from "../../data/books";
import { useStockByIds } from "../../hooks/useStockByIds";

type Props = { relatedBooks: Book["relatedBooks"] };

const RelatedBooks: React.FC<Props> = ({ relatedBooks }) => {
  const navigate = useNavigate();
  const ids = (relatedBooks ?? []).map((r) => r.id);
  const { data: stockMap, loading } = useStockByIds(ids);

  if (!relatedBooks || relatedBooks.length === 0) return null;

  return (
    <div className="bg-background rounded-lg shadow-lg p-8 mb-16">
      <h2 className="text-2xl font-bold text-primary mb-4">Livros do Mesmo Autor</h2>
      {loading && <p className="text-sm text-gray-500 mb-4">Carregando relacionados…</p>}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-12">
        {relatedBooks.map((book) => {
          const s = stockMap[book.id]?.stock;
          const hasStockInfo = typeof s === "number";
          const isAvailable = hasStockInfo ? s! > 0 : true; // otimista
          const cardClasses = `bg-background rounded-lg shadow-md overflow-hidden transition transform ${
            isAvailable ? "cursor-pointer hover:scale-105" : "cursor-not-allowed opacity-70"
          }`;

          return (
            <div
              key={book.id}
              className={cardClasses}
              onClick={() => { if (isAvailable) navigate(`/books/${book.id}`); }}
              role="button"
              aria-disabled={!isAvailable}
              tabIndex={0}
              onKeyDown={(e) => {
                if (isAvailable && (e.key === "Enter" || e.key === " ")) navigate(`/books/${book.id}`);
              }}
            >
              <div className="relative">
                <img src={book.imageUrl} alt={book.title} className="w-full h-72 object-cover rounded-t-lg" />

                {hasStockInfo && s! <= 0 && (
                  <span className="absolute top-3 left-3 px-3 py-1 text-xs bg-gray-300 text-gray-800 rounded">Esgotado</span>
                )}
                {hasStockInfo && s! > 0 && s! <= 5 && (
                  <span className="absolute top-3 left-3 px-3 py-1 text-xs bg-yellow-200 text-yellow-900 rounded">
                    Últimas ({s})
                  </span>
                )}
              </div>

              <div className="p-4 text-center">
                <h3 className="text-md font-semibold text-primary uppercase">{book.category}</h3>
                <h4 className="text-lg font-bold text-primary mt-1 line-clamp-2">{book.title}</h4>
                <p className="text-secondary font-semibold mt-1">{book.price}</p>
              </div>

              <div className="px-4 pb-4">
                <button
                  type="button"
                  disabled={!isAvailable}
                  onClick={(e) => {
                    e.stopPropagation();
                    if (isAvailable) navigate(`/books/${book.id}`);
                  }}
                  className={`w-full text-center px-3 py-2 rounded-md transition ${
                    isAvailable ? "bg-blue-600 text-white hover:bg-blue-700" : "bg-gray-300 text-gray-600"
                  }`}
                >
                  {isAvailable ? "Ver detalhes" : "Esgotado"}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default RelatedBooks;
