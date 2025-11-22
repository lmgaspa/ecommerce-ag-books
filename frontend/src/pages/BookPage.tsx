import { useMemo } from "react";
import { useParams } from "react-router-dom";
import { books } from "../data/books";
import BookDetails from "../components/book/BookDetails";
import { useStockByIds } from "../hooks/useStockByIds";

export default function BookPage() {
  const { id } = useParams<{ id: string }>();
  const book = useMemo(() => books.find((b) => b.id === id), [id]);
  const idArr = useMemo(() => (id ? [id] : []), [id]);
  const { data: stockMap, loading } = useStockByIds(idArr);

  if (!book) return <p>Livro não encontrado</p>;

  const realStock =
    typeof stockMap[book.id]?.stock === "number"
      ? stockMap[book.id]!.stock
      : book.stock ?? 0;

  return <BookDetails {...book} stock={realStock} loading={loading} />;
}
