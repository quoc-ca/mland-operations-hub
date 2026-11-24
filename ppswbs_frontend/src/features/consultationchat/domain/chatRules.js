// Feature-013 Chat Domain Rules
export const MAX_MESSAGE_LENGTH = 2000;
export const POLLING_INTERVAL_MS = 3000;

export function countCodePoints(str) {
  if (!str) return 0;
  return Array.from(str).length;
}

export function validateMessageText(text) {
  if (!text || !text.trim()) {
    return 'Nội dung tin nhắn không được để trống.';
  }
  const len = countCodePoints(text.trim());
  if (len > MAX_MESSAGE_LENGTH) {
    return `Tin nhắn vượt quá giới hạn 2.000 ký tự (hiện tại: ${len}).`;
  }
  return null;
}
