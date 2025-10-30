interface PixPayloadParams {
  key: string; // chave Pix (email, telefone,  ou aleatória)
  name: string;
  city: string;
  amount: number;
}

export function generatePixPayload({ key, name, city, amount }: PixPayloadParams): string {
  const format = (id: string, value: string): string => {
    const size = String(value.length).padStart(2, '0');
    return `${id}${size}${value}`;
  };

  const merchantAccount = format('00', 'BR.GOV.BCB.PIX') + format('01', key);
  const merchantAccountField = format('26', merchantAccount);

  const payloadArray = [
    '000201',
    merchantAccountField,
    '52040000',
    '5303986',
    format('54', amount.toFixed(2)), // correto
    '5802BR',
    format('59', name),
    format('60', city),
    '62070503***'
  ];

  const payloadWithoutCRC = payloadArray.join('') + '6304';

  const crc16 = (str: string): string => {
    let crc = 0xFFFF;
    for (let i = 0; i < str.length; i++) {
      crc ^= str.charCodeAt(i) << 8;
      for (let j = 0; j < 8; j++) {
        crc = (crc & 0x8000) ? ((crc << 1) ^ 0x1021) : (crc << 1);
      }
    }
    return (crc & 0xFFFF).toString(16).toUpperCase().padStart(4, '0');
  };

  const crc = crc16(payloadWithoutCRC);
  return payloadWithoutCRC + crc;
}

