-- V2__seed_books.sql
-- Seed inicial de livros do catálogo AG Books

INSERT INTO books (id, author, category, description, image_url, price, stock, title, author_id)
VALUES
  (
    'extase',
    'Agenor Gasparetto',
    'Crônicas',
    'Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas.',
    'https://www.agenorgasparetto.com.br/images/extase.webp',
    30.00,
    100,
    'Êxtase, de birra com Jorge Amado e outras crônicas grapiúnas',
    1
  ),
  (
    'sempre',
    'Agenor Gasparetto',
    'Crônicas',
    'Crônicas e memórias afetivas entre gerações.',
    'https://www.agenorgasparetto.com.br/images/sempre.webp',
    20.00,
    100,
    'Para sempre felizes: coisas de neto',
    1
  ),
  (
    'versos',
    'Agenor Gasparetto',
    'Poesia',
    'Versos desnudos em tempos tensos.',
    'https://www.agenorgasparetto.com.br/images/versos.webp',
    20.00,
    100,
    'Versos Desnudos, Poemas em tempo Tensos',
    1
  ),
  (
    'versi',
    'Agenor Gasparetto',
    'Poesia',
    'Edição italiana: poesie in tempi difficili.',
    'https://www.agenorgasparetto.com.br/images/versi.webp',
    30.00,
    100,
    'Versi spogli: poesie in tempi difficili',
    1
  ),
  (
    'regressantes',
    'Agenor Gasparetto',
    'Ficção',
    'Romance sobre regressos, memória e pertencimento.',
    'https://www.agenorgasparetto.com.br/images/regressantes.webp',
    30.00,
    100,
    'Regressantes',
    1
  )
ON CONFLICT (id) DO UPDATE
SET
  author      = EXCLUDED.author,
  category    = EXCLUDED.category,
  description = EXCLUDED.description,
  image_url   = EXCLUDED.image_url,
  price       = EXCLUDED.price,
  stock       = EXCLUDED.stock,
  title       = EXCLUDED.title,
  author_id   = EXCLUDED.author_id;

-- Vincula todos esses livros ao autor de pagamento (payment_author_registry.id = 1)
INSERT INTO payment_book_authors (book_id, author_id)
SELECT b.id, 1
FROM books b
WHERE b.id IN ('extase','sempre','versos','versi','regressantes')
ON CONFLICT (book_id) DO UPDATE
SET author_id = EXCLUDED.author_id;
