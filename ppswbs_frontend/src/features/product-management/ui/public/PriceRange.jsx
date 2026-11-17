import React from 'react';

/** One price, or "minimum – maximum" when the sizes differ. */
export function PriceRange({ range, format }) {
  if (!range?.minimum) return <span className="price">—</span>;
  if (!range.maximum || range.maximum === range.minimum) return <span className="price">{format.money(range.minimum)}</span>;
  return <span className="price">{format.money(range.minimum)} – {format.money(range.maximum)}</span>;
}
