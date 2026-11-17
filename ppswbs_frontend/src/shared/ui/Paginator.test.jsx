import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Paginator, usePaged } from './Paginator';

function Demo({ count, size }) {
  const paged = usePaged(Array.from({ length: count }, (_, i) => `item-${i + 1}`), size, count);
  return (
    <>
      <ul>{paged.pageItems.map(item => <li key={item}>{item}</li>)}</ul>
      <Paginator page={paged.page} pageCount={paged.pageCount} onChange={paged.setPage} />
    </>
  );
}

describe('Paginator', () => {
  it('pages a list and jumps to any page', async () => {
    const user = userEvent.setup();
    render(<Demo count={25} size={10} />);
    expect(screen.getAllByRole('listitem')).toHaveLength(10);
    await user.click(screen.getByRole('button', { name: 'Sau ›' }));
    expect(screen.getByText('item-11')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Chọn trang'), '3');
    expect(screen.getAllByRole('listitem')).toHaveLength(5);
    expect(screen.getByRole('button', { name: 'Sau ›' })).toBeDisabled();
  });

  it('renders no controls when one page is enough', () => {
    render(<Demo count={8} size={10} />);
    expect(screen.queryByRole('navigation')).toBeNull();
  });
});
