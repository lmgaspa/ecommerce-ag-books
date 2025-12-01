// src/data/booksWithRelated.ts
import { books as rawBooks } from "./books";

export interface RawBook {
  id: string;
  title: string;
  imageUrl: string;
  price: string;
  description: string;
  author: string;
  additionalInfo: Record<string, string>;
  category: string;
  stock: number;
}

export interface BookWithRelated extends RawBook {
  relatedBooks: {
    id: string;
    title: string;
    imageUrl: string;
    price: string;
    category: string;
  }[];
}

/**
 * Embaralha e devolve `count` itens únicos do array.
 * Não repete porque faz slice de um array sem duplicatas.
 */
const getRandomBooks = (books: RawBook[], count: number): RawBook[] => {
  const shuffled = [...books];

  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }

  return shuffled.slice(0, count);
};

/**
 * Regras por autor (N = total de livros desse autor):
 * 1 → 0 relacionados
 * 2 → 1 relacionado
 * 3 → 2 relacionados
 * 4 → 3 relacionados (já aleatório)
 * 5+ → 3 relacionados aleatórios (sem repetição)
 *
 * Implementação: relacionados = random(min(N - 1, 3))
 */
export const booksWithRelated: BookWithRelated[] = rawBooks.map((book) => {
  const { relatedBooks: _ignored, ...bookWithoutRelated } = book as any;

  // todos os outros livros do mesmo autor
  const sameAuthorBooks: RawBook[] = rawBooks
    .map((b) => {
      const { relatedBooks: _ignoredInner, ...bWithoutRelated } = b as any;
      return bWithoutRelated as RawBook;
    })
    .filter((b) => b.author === book.author && b.id !== book.id);

  const totalSameAuthor = sameAuthorBooks.length;
  const maxRelated = Math.min(totalSameAuthor, 3);

  const related =
    maxRelated > 0 ? getRandomBooks(sameAuthorBooks, maxRelated) : [];

  return {
    ...bookWithoutRelated,
    relatedBooks: related.map((b) => ({
      id: b.id,
      title: b.title,
      imageUrl: b.imageUrl,
      price: b.price,
      category: b.category,
    })),
  };
});

// export compatível com o resto do app
export const books = booksWithRelated;
export type Book = BookWithRelated;
