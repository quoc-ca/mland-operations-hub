import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PackageAvailabilityMatrix from '../components/PackageAvailabilityMatrix';

const packages = [
  { id: 1, name: 'Signature Brass', lifecycleState: 'PUBLISHED' },
  { id: 2, name: 'Silver Clay 5g', lifecycleState: 'PUBLISHED' },
];
const locations = [
  { id: 10, name: 'Hoàn Kiếm', active: true },
  { id: 11, name: 'Tây Hồ', active: true },
  { id: 12, name: 'Đà Nẵng', active: false },
];
// 1@10 ACTIVE v2, 2@10 INACTIVE v1, 1@11 missing, 2@11 ACTIVE v0 ... Đà Nẵng has no rows.
const cells = [
  { packageId: 1, locationId: 10, state: 'ACTIVE', version: 2 },
  { packageId: 2, locationId: 10, state: 'INACTIVE', version: 1 },
  { packageId: 2, locationId: 11, state: 'ACTIVE', version: 0 },
];

const setup = (props = {}) => {
  const onToggle = vi.fn();
  render(<PackageAvailabilityMatrix packages={packages} locations={locations} cells={cells} onToggle={onToggle} layout="table" {...props} />);
  return onToggle;
};
const box = name => screen.getByRole('checkbox', { name });

describe('PackageAvailabilityMatrix (desktop table)', () => {
  it('ticks ACTIVE cells and leaves INACTIVE and missing cells unticked', () => {
    setup();
    expect(box('Signature Brass tại Hoàn Kiếm')).toBeChecked();
    expect(box('Silver Clay 5g tại Hoàn Kiếm')).not.toBeChecked();
    expect(box('Signature Brass tại Tây Hồ')).not.toBeChecked();
    expect(box('Silver Clay 5g tại Tây Hồ')).toBeChecked();
  });

  it('gives every checkbox the accessible name "<package> tại <location>"', () => {
    setup();
    expect(screen.getAllByRole('checkbox')).toHaveLength(6);
    expect(box('Silver Clay 5g tại Đà Nẵng')).toBeInTheDocument();
  });

  it('keeps an inactive location row visible with enabled, tickable checkboxes', async () => {
    const onToggle = setup();
    expect(screen.getByText('Đà Nẵng')).toBeInTheDocument();
    expect(screen.getByText('Chưa hoạt động')).toBeInTheDocument();
    const cell = box('Signature Brass tại Đà Nẵng');
    expect(cell).toBeEnabled();
    expect(cell).not.toHaveAttribute('aria-disabled');
    await userEvent.setup().click(cell);
    expect(onToggle).toHaveBeenCalledWith(1, 12, 'ACTIVE');
  });

  it('click requests the opposite state', async () => {
    const onToggle = setup();
    const user = userEvent.setup();
    await user.click(box('Signature Brass tại Hoàn Kiếm'));
    expect(onToggle).toHaveBeenLastCalledWith(1, 10, 'INACTIVE');
    await user.click(box('Silver Clay 5g tại Hoàn Kiếm'));
    expect(onToggle).toHaveBeenLastCalledWith(2, 10, 'ACTIVE');
  });

  it('is operable with the keyboard (Tab then Space)', async () => {
    const onToggle = setup();
    const user = userEvent.setup();
    await user.tab();
    expect(box('Signature Brass tại Hoàn Kiếm')).toHaveFocus();
    await user.keyboard(' ');
    expect(onToggle).toHaveBeenCalledWith(1, 10, 'INACTIVE');
  });

  it('a saving cell is aria-disabled with "Đang lưu", ignores clicks, and other cells stay usable', async () => {
    const onToggle = setup({ savingCells: { '2:11': true } });
    const user = userEvent.setup();
    const saving = box('Silver Clay 5g tại Tây Hồ');
    expect(saving).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText('Đang lưu')).toBeInTheDocument();
    await user.click(saving);
    expect(onToggle).not.toHaveBeenCalled();
    const other = box('Signature Brass tại Hoàn Kiếm');
    expect(other).not.toHaveAttribute('aria-disabled');
    await user.click(other);
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('marks conflict and error cells without changing their value', () => {
    setup({ cellErrors: { '2:10': 'conflict', '1:11': 'error' } });
    const conflict = box('Silver Clay 5g tại Hoàn Kiếm');
    const error = box('Signature Brass tại Tây Hồ');
    expect(conflict.closest('.pam-cell')).toHaveAttribute('data-error', 'conflict');
    expect(error.closest('.pam-cell')).toHaveAttribute('data-error', 'error');
    expect(box('Signature Brass tại Hoàn Kiếm').closest('.pam-cell')).not.toHaveAttribute('data-error');
  });

  it('shows no lifecycle badge, confirmation or Save button', () => {
    setup();
    expect(screen.queryByText('PUBLISHED')).toBeNull();
    expect(screen.queryByRole('button')).toBeNull();
  });
});

describe('PackageAvailabilityMatrix (mobile cards)', () => {
  it('renders one card per location with one checkbox per package, no table', async () => {
    const onToggle = setup({ layout: 'cards' });
    expect(screen.queryByRole('table')).toBeNull();
    const cards = screen.getAllByRole('region');
    expect(cards).toHaveLength(3);
    expect(within(cards[2]).getAllByRole('checkbox')).toHaveLength(2);
    expect(within(cards[2]).getByText('Chưa hoạt động')).toBeInTheDocument();
    const cell = box('Silver Clay 5g tại Đà Nẵng');
    expect(cell).toBeEnabled();
    await userEvent.setup().click(cell);
    expect(onToggle).toHaveBeenCalledWith(2, 12, 'ACTIVE');
  });
});
