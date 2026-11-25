/**
 * Phân tích cú pháp nội dung file .env tương tự Vercel
 * - Bỏ qua comment (#) và dòng trống
 * - Loại bỏ tiền tố 'export '
 * - Tách cặp KEY=VALUE tại dấu '=' đầu tiên
 * - Bỏ dấu nháy kép / nháy đơn bọc ngoài value
 * - Nếu dòng chỉ có key mà không có '=', value được gán là ''
 * 
 * @param {string} content Nội dung file .env dạng text
 * @returns {Array<{key: string, value: string}>} Danh sách biến môi trường
 */
export function parseEnvContent(content) {
  if (!content || typeof content !== 'string') return [{ key: '', value: '' }]

  const lines = content.split(/\r?\n/)
  const result = []

  for (let rawLine of lines) {
    let line = rawLine.trim()
    if (!line || line.startsWith('#')) continue

    // Loại bỏ tiền tố 'export ' nếu có
    if (/^export\s+/i.test(line)) {
      line = line.replace(/^export\s+/i, '').trim()
    }

    const firstEq = line.indexOf('=')
    if (firstEq === -1) {
      // Dòng không có dấu '=': vẫn import tên key nhưng value rỗng
      const key = line.trim()
      if (key) {
        result.push({ key, value: '' })
      }
    } else {
      const key = line.slice(0, firstEq).trim()
      let value = line.slice(firstEq + 1).trim()

      // Bỏ cặp dấu ngoặc kép hoặc đơn bao quanh giá trị nếu có
      if (
        (value.startsWith('"') && value.endsWith('"') && value.length >= 2) ||
        (value.startsWith("'") && value.endsWith("'") && value.length >= 2)
      ) {
        value = value.slice(1, -1)
      }

      if (key) {
        result.push({ key, value })
      }
    }
  }

  return result.length > 0 ? result : [{ key: '', value: '' }]
}
