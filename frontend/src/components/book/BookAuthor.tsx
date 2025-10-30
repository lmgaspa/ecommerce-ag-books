import React from 'react';

interface BookAuthorProps {
  author: string;
}

const BookAuthor: React.FC<BookAuthorProps> = ({ author }) => (
  <div className="mb-8">
    <p className="text-md text-text-secondary leading-relaxed">Autor: {author}</p>
  </div>
);

export default BookAuthor;
