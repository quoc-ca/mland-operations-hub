import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TicketSessionPicker } from './TicketSessionPicker';

/** 003.3 FR-044a (T181): a two-session ticket is answered one session at a time; both together returns pairs. */

const A = 'aaaaaaaa-0000-4000-8000-000000000001';
const B = 'aaaaaaaa-0000-4000-8000-000000000002';
const A2 = 'bbbbbbbb-0000-4000-8000-000000000001';
const B2 = 'bbbbbbbb-0000-4000-8000-000000000002';
const win = (start, end) => `${start}|${end}`;

const base = {
  allowance: { exhausted: false }, windowOpen: true,
  perFromSession: [
    { fromSessionReference: A, start: 's1', end: 'e1', sourceChangeable: true, options: [{ sessionReference: A2, start: 's3', end: 'e3', remainingCapacity: 2, selectable: true }] },
    { fromSessionReference: B, start: 's2', end: 'e2', sourceChangeable: true, options: [] },
  ],
};

describe('TicketSessionPicker', () => {
  it('lists one session at a time and reports the choice as one from and one target', async () => {
    const loadOptions = vi.fn(async () => base);
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<TicketSessionPicker ticketCode="T1" type="ADVANCE" loadOptions={loadOptions} onSelect={onSelect} formatWindow={win} />);

    await user.click((await screen.findAllByRole('radio'))[0]);
    const select = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === 'to-T1');
    await user.selectOptions(select, A2);

    expect(onSelect).toHaveBeenLastCalledWith(expect.objectContaining({ fromSessionReferences: [A], targetSessionReferences: [A2], requiresManager: undefined }));
    expect(loadOptions).toHaveBeenCalledWith([]);
  });

  it('asks for both sessions and offers the pairs, reporting both from and both targets', async () => {
    const loadOptions = vi.fn(async from => (from.length === 2
      ? { ...base, pairs: [{ sessionReferences: [A2, B2], selectable: true, requiresManager: true, managerRules: ['RESCHEDULE_QUOTA_EXHAUSTED'], priceIncreaseAmountVnd: '0',
        sessions: [{ sessionReference: A2, start: 's3', end: 'e3', remainingCapacity: 2 }, { sessionReference: B2, start: 's4', end: 'e4', remainingCapacity: 1 }] }] }
      : base));
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<TicketSessionPicker ticketCode="T1" type="ADVANCE" loadOptions={loadOptions} onSelect={onSelect} formatWindow={win} />);

    await user.click(await screen.findByRole('radio', { name: /Both sessions together/ }));
    expect(loadOptions).toHaveBeenLastCalledWith([A, B]);
    const pairSelect = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === 'pair-T1');
    await user.selectOptions(pairSelect, `${A2}>${B2}`);

    expect(onSelect).toHaveBeenLastCalledWith(expect.objectContaining({
      fromSessionReferences: [A, B], targetSessionReferences: [A2, B2], requiresManager: true,
    }));
  });

  it('disables a session that cannot be changed and offers no pair option then', async () => {
    const data = { ...base, perFromSession: [{ ...base.perFromSession[0], sourceChangeable: false, sourceReasonCode: 'INVALID_REPLACEMENT', options: [] }, base.perFromSession[1]] };
    render(<TicketSessionPicker ticketCode="T1" type="ADVANCE" loadOptions={async () => data} onSelect={() => {}} formatWindow={win} />);

    const radios = await screen.findAllByRole('radio');
    expect(radios[0]).toBeDisabled();
    expect(screen.queryByRole('radio', { name: /Both sessions together/ })).toBeNull();
  });

  it('skips the session choice for a one-session ticket and lists the new sessions straight away', async () => {
    const data = { ...base, perFromSession: [base.perFromSession[0]] };
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<TicketSessionPicker ticketCode="T1" type="ADVANCE" loadOptions={async () => data} onSelect={onSelect} formatWindow={win} />);

    const select = (await screen.findAllByRole('combobox')).find(item => item.getAttribute('name') === 'to-T1');
    expect(screen.queryByRole('radio')).toBeNull();
    await user.selectOptions(select, A2);

    expect(onSelect).toHaveBeenLastCalledWith(expect.objectContaining({ fromSessionReferences: [A], targetSessionReferences: [A2] }));
  });

  it('clears the choice when the type changes and reloads the list', async () => {
    const loadOptions = vi.fn(async () => base);
    const onSelect = vi.fn();
    const { rerender } = render(<TicketSessionPicker ticketCode="T1" type="ADVANCE" loadOptions={loadOptions} onSelect={onSelect} formatWindow={win} />);
    await screen.findAllByRole('radio');
    rerender(<TicketSessionPicker ticketCode="T1" type="LATE_ARRIVAL" loadOptions={loadOptions} onSelect={onSelect} formatWindow={win} />);
    await screen.findAllByRole('radio');

    expect(loadOptions).toHaveBeenCalledTimes(2);
    expect(onSelect).toHaveBeenLastCalledWith(null);
  });
});
