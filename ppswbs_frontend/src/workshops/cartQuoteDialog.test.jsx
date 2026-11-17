import React, { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CartQuoteDialog } from './CartQuoteDialog';

function Host({ busy = false }) {
  const [open, setOpen] = useState(false);
  return <div>
    <button onClick={() => setOpen(true)}>open</button>
    <CartQuoteDialog open={open} title="Báo giá" busy={busy} onClose={() => setOpen(false)}>
      <button>inside</button>
    </CartQuoteDialog>
  </div>;
}

describe('CartQuoteDialog', () => {
  it('opens as a modal dialog, closes on Escape and gives focus back to the opener', async () => {
    const user = userEvent.setup();
    render(<Host />);
    expect(screen.queryByRole('dialog')).toBeNull();
    await user.click(screen.getByText('open'));
    const dialog = screen.getByRole('dialog', { name: 'Báo giá' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveFocus();
    expect(document.body.style.overflow).toBe('hidden');
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByText('open')).toHaveFocus();
    expect(document.body.style.overflow).not.toBe('hidden');
  });

  it('keeps Tab inside the window', async () => {
    const user = userEvent.setup();
    render(<Host />);
    await user.click(screen.getByText('open'));
    await user.tab(); // close button
    await user.tab(); // inside
    await user.tab(); // wraps to the close button
    expect(screen.getByRole('button', { name: 'Đóng báo giá' })).toHaveFocus();
  });

  it('closes with the × button and on a click on the dim background', async () => {
    const user = userEvent.setup();
    const { container } = render(<Host />);
    await user.click(screen.getByText('open'));
    await user.click(screen.getByRole('button', { name: 'Đóng báo giá' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    await user.click(screen.getByText('open'));
    await user.click(container.ownerDocument.querySelector('.quote-dialog-backdrop'));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('cannot be closed while the booking is being confirmed', async () => {
    const user = userEvent.setup();
    render(<Host busy />);
    await user.click(screen.getByText('open'));
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đóng báo giá' })).toBeDisabled();
  });
});
