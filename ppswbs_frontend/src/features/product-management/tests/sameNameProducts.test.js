import { describe, expect, it, vi } from 'vitest';
import { findSameName, findSameNames, sameNameSearchText } from '../application/sameNameProducts';

// FR-056 / T143: the list search is a substring match, so the search text must be the normalized name. A name typed
// with extra inner spaces ("nhẫn  BẠC trơn") used to be sent as is and found nothing.
const page = items => ({ items, page: 0, pageSize: 20, totalElements: String(items.length) });

describe('same-name lookup (FR-056)', () => {
  it('searches with the name trimmed, inner spaces collapsed and in NFC, keeping case', () => {
    expect(sameNameSearchText('  nhẫn  BẠC \t trơn ')).toBe('nhẫn BẠC trơn');
    expect(sameNameSearchText('nhẵn')).toBe('nhẵn'.normalize('NFC'));
    expect(sameNameSearchText(undefined)).toBe('');
  });

  it('finds the products of a name typed with extra spaces', async () => {
    const listProducts = vi.fn(async ({ q }) => (q === 'nhẫn BẠC trơn'
      ? page([{ id: '1', name: 'Nhẫn bạc trơn' }, { id: '2', name: 'Nhẫn bạc trơn xinh' }])
      : page([])));
    const found = await findSameName({ listProducts }, '  nhẫn  BẠC trơn ');
    expect(listProducts).toHaveBeenCalledWith({ q: 'nhẫn BẠC trơn', page: undefined });
    expect(found.map(item => item.id)).toEqual(['1']);
  });

  it('import names use the same search text', async () => {
    const listProducts = vi.fn(async () => page([{ id: '5', name: 'Nhẫn cưới' }]));
    const found = await findSameNames({ listProducts }, ['Nhẫn   cưới']);
    expect(listProducts).toHaveBeenCalledWith({ q: 'Nhẫn cưới', page: undefined });
    expect(Object.values(found).flat().map(item => item.id)).toEqual(['5']);
  });

  it('a failed read gives no warning', async () => {
    const listProducts = vi.fn(async () => { throw new Error('down'); });
    expect(await findSameName({ listProducts }, 'Nhẫn')).toEqual([]);
  });
});
