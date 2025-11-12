-- Cria tabela authors se não existir
CREATE TABLE IF NOT EXISTS authors (
  id    BIGSERIAL PRIMARY KEY,
  name  VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL
);

-- Índice simples em email (opcional, ajuda em buscas exatas)
CREATE INDEX IF NOT EXISTS ix_authors_email ON authors (email);
