/**
 * Text for a contract Money object ({ amount, currency }). A whole-number VND amount goes through the shared
 * money formatter; any other amount/currency is shown as received, followed by its currency code.
 */
export function moneyText(fmt, money) {
  const amount = money?.amount || '0';
  const currency = money?.currency || 'VND';
  return currency === 'VND' && /^\d+$/.test(String(amount)) ? fmt.money(amount) : `${amount} ${currency}`;
}
