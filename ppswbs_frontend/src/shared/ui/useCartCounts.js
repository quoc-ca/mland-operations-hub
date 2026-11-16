import { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { useHttp } from '../api/useHttp';
import { cartLineCount, subscribeToCart } from '../../workshops/cartStorage';
import { RETAIL_CART_CHANGED, retailApi } from '../../features/retail-order/infrastructure/retailApi';

/**
 * Line counts of the two carts shown under the one "Giỏ hàng" entry: the Member's product cart (server, signed-in
 * only) and the workshop cart (this browser). Counts are display hints only; each cart page stays authoritative.
 */
export function useCartCounts() {
  const { status } = useAuth();
  const http = useHttp();
  const location = useLocation();
  const signedIn = status === 'signed-in';
  const [workshop, setWorkshop] = useState(() => cartLineCount());
  const [product, setProduct] = useState(0);

  useEffect(() => subscribeToCart(lines => setWorkshop(lines.length)), []);

  const refreshProduct = useCallback(() => {
    if (!signedIn) { setProduct(0); return () => {}; }
    let live = true;
    retailApi(http).getCart()
      .then(cart => { if (live) setProduct(Array.isArray(cart?.lines) ? cart.lines.length : 0); })
      .catch(() => { if (live) setProduct(0); });
    return () => { live = false; };
  }, [signedIn, http]);

  useEffect(() => refreshProduct(), [refreshProduct, location.pathname]);
  useEffect(() => {
    const onChange = () => refreshProduct();
    window.addEventListener(RETAIL_CART_CHANGED, onChange);
    return () => window.removeEventListener(RETAIL_CART_CHANGED, onChange);
  }, [refreshProduct]);

  return { product, workshop, total: product + workshop };
}
