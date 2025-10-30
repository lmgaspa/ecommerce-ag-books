interface ContentBlockProps {
  title: string;
  imageUrl: string;
  description: string;
  isAuthor?: boolean;
}

const ContentBlock: React.FC<ContentBlockProps> = ({ title, imageUrl, description, isAuthor = false }) => {
  return (
    <div 
      className={`bg-background2 rounded-lg shadow-lg overflow-hidden flex flex-col items-center p-8 ${
        isAuthor ? "max-w-5xl mx-auto mb-16" : "w-full max-w-sm mb-16"
      }`}
    >
      <div className={`${isAuthor ? "w-64 h-64" : "w-64 h-64"} rounded-full bg-background2 shadow-lg overflow-hidden flex items-center justify-center mb-6`}>
        <img 
          src={imageUrl} 
          alt={title} 
          className="object-contain h-full" 
        />
      </div>
      <h2 className={`text-2xl font-extrabold text-primary mb-4 text-center ${isAuthor ? "text-4xl" : ""}`}>
        {title}
      </h2>
      <p className="text-lg text-text-secondary text-center">
        {description}
      </p>
    </div>
  );
};

export default ContentBlock;
