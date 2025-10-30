import React from 'react';

interface BookDescriptionProps {
  description: string;
}

const BookDescription: React.FC<BookDescriptionProps> = ({ description }) => {
  return (
    <div
      className="text-text-secondary mb-4"
      dangerouslySetInnerHTML={{ __html: description }}
    />
  );
};

export default BookDescription;
