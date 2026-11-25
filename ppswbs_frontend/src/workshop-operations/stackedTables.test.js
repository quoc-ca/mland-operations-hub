import { describe, expect, it } from 'vitest';
import { labelTableCells } from './stackedTables';

describe('labelTableCells', () => {
  it('copies header text to data-label and skips colspan cells', () => {
    document.body.innerHTML = `<table class="ops-table"><thead><tr><th>Date</th><th>Status</th></tr></thead>
      <tbody><tr><td>1</td><td>OK</td></tr><tr><td colspan="2">edit</td></tr></tbody></table>`;
    labelTableCells();
    const cells = document.querySelectorAll('td');
    expect(cells[0].getAttribute('data-label')).toBe('Date');
    expect(cells[1].getAttribute('data-label')).toBe('Status');
    expect(cells[2].hasAttribute('data-label')).toBe(false);
  });
});
