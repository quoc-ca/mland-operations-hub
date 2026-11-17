import React from 'react';
import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BuyerProof } from '../ui';

describe('buyer verification contact', () => {
  it('keeps exactly one contact: typing the phone clears an email the browser autofilled, and the reverse', async () => {
    const user = userEvent.setup();
    render(<BuyerProof groupCode="GRP-1" onReference={() => {}} />);

    // The browser fills the email by itself (autofill), without the user typing it.
    fireEvent.change(screen.getByLabelText('Contact email'), { target: { value: 'hoang@example.com' } });
    expect(screen.getByLabelText('Contact email')).toHaveValue('hoang@example.com');

    await user.type(screen.getByRole('textbox', { name: /Contact phone/i }), '338905173');
    expect(screen.getByLabelText('Contact email')).toHaveValue('');
    expect(screen.queryByText(/Enter one valid email or one valid phone number/)).toBeNull();
    expect(screen.getByRole('button', { name: 'Send code' })).toBeEnabled();

    await user.type(screen.getByLabelText('Contact email'), 'buyer@example.com');
    expect(screen.getByRole('textbox', { name: /Contact phone/i })).toHaveValue('');
  });

  it('says the code goes to the booking email, not by SMS', () => {
    render(<BuyerProof groupCode="GRP-1" onReference={() => {}} />);
    expect(screen.getByText(/always sent to the booking's email on file, not by SMS/)).toBeInTheDocument();
  });
});
