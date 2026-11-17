import React from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider, useSearchParams } from 'react-router';
import { useUrlFilters } from './useUrlFilters';

const DEFAULTS = { status: 'PENDING', q: '' };

function Probe() {
  const [filters, setFilters] = useUrlFilters(DEFAULTS);
  const [params] = useSearchParams();
  return <div>
    <output data-testid="filters">{JSON.stringify(filters)}</output>
    <output data-testid="search">{params.toString()}</output>
    <button onClick={() => setFilters({ ...filters, status: '' })}>all</button>
    <button onClick={() => setFilters(previous => ({ ...previous, q: 'abc' }))}>search</button>
    <button onClick={() => setFilters({ status: 'PENDING', q: '' })}>reset</button>
  </div>;
}

const open = path => {
  const router = createMemoryRouter([{ path: '/list', element: <Probe /> }, { path: '/detail', element: <p>detail</p> }], { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
};

describe('useUrlFilters', () => {
  it('uses the defaults when the URL has no filters and leaves the URL clean', () => {
    open('/list');
    expect(JSON.parse(screen.getByTestId('filters').textContent)).toEqual(DEFAULTS);
    expect(screen.getByTestId('search').textContent).toBe('');
  });

  it('reads filters from the URL', () => {
    open('/list?status=DONE&q=x');
    expect(JSON.parse(screen.getByTestId('filters').textContent)).toEqual({ status: 'DONE', q: 'x' });
  });

  it('keeps an empty value that differs from its default, and drops a value equal to its default', async () => {
    const user = userEvent.setup();
    open('/list');
    await user.click(screen.getByText('all'));
    expect(screen.getByTestId('search').textContent).toBe('status=');
    expect(JSON.parse(screen.getByTestId('filters').textContent).status).toBe('');
    await user.click(screen.getByText('reset'));
    expect(screen.getByTestId('search').textContent).toBe('');
  });

  it('replaces history, so Back from a detail page returns to the same filters without extra entries', async () => {
    const user = userEvent.setup();
    const router = open('/list');
    await user.click(screen.getByText('search'));
    await user.click(screen.getByText('all'));
    const length = router.state.historyAction;
    expect(length).toBe('REPLACE');
    await router.navigate('/detail');
    await router.navigate(-1);
    expect(JSON.parse(screen.getByTestId('filters').textContent)).toEqual({ status: '', q: 'abc' });
  });
});
