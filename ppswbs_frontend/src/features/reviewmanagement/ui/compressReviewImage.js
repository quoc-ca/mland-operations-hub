// Review photos travel as data URLs inside a JSON body, so they are shrunk first: longest side 1600px, JPEG 0.8.
// A phone photo of several MB becomes a few hundred KB, which keeps the request small enough for the hosting proxy.
export const MAX_SIDE = 1600;
export const JPEG_QUALITY = 0.8;

const readAsDataUrl = file => new Promise((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => resolve(reader.result);
  reader.onerror = reject;
  reader.readAsDataURL(file);
});

const loadImage = src => new Promise((resolve, reject) => {
  const img = new Image();
  img.onload = () => resolve(img);
  img.onerror = reject;
  img.src = src;
});

/** Resolves { dataUrl, size, name }; falls back to the original file when the browser cannot decode or draw it. */
export async function compressReviewImage(file) {
  const original = await readAsDataUrl(file);
  try {
    const img = await loadImage(original);
    const scale = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff'; // JPEG has no transparency
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', JPEG_QUALITY);
    if (dataUrl.startsWith('data:image/jpeg') && dataUrl.length < original.length) {
      return { dataUrl, size: Math.round((dataUrl.length * 3) / 4), name: file.name };
    }
  } catch {
    /* keep the original below */
  }
  return { dataUrl: original, size: file.size, name: file.name };
}
