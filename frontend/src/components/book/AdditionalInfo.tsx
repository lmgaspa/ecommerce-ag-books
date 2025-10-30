import React from 'react';

interface AdditionalInfoProps {
  additionalInfo: Record<string, string>;
}

const AdditionalInfo: React.FC<AdditionalInfoProps> = ({ additionalInfo }) => (
  <div className="bg-background rounded-lg shadow-lg p-8 mb-16">
    <h2 className="text-2xl font-bold text-primary mb-4">Informação Adicional</h2>
    <table className="w-full text-left border-t border-b border-primary">
      <tbody>
        {Object.entries(additionalInfo).map(([key, value]) => (
          <tr key={key} className="border-t border-primary">
            <th className="font-semibold py-3 px-4 text-primary bg-background">{key}</th>
            <td className="py-3 px-4 text-text-secondary">{value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

export default AdditionalInfo;
