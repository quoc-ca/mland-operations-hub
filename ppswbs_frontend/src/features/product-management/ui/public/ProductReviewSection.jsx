import React, { useEffect, useState } from 'react';
import { useReviewManagementApi, ReviewCard, formatStars } from '../../../reviewmanagement';
import { Loading } from '../../../../shared/ui/components';
import './product-reviews.css';

export default function ProductReviewSection({ productId, productName }) {
  const api = useReviewManagementApi();
  const [sort, setSort] = useState('recent'); // 'recent' | 'best_to_worse' | 'worse_to_best'
  const [ratingFilter, setRatingFilter] = useState(null); // null (All) | 5 | 4 | 3 | 2 | 1
  const [data, setData] = useState({ items: [], aggregate: { count: 0, averageRating: 0 } });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!productId) return;
    let active = true;

    async function fetchReviews() {
      setLoading(true);
      setError(null);
      try {
        const res = await api.listPublicReviews({
          targetType: 'RETAIL_PRODUCT',
          targetReference: String(productId),
          rating: ratingFilter || undefined,
          sort,
          limit: 50,
        });
        if (active && res) {
          setData(res);
        }
      } catch (err) {
        if (active) {
          setError(err.message || 'Không thể tải đánh giá sản phẩm.');
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    fetchReviews();
    return () => {
      active = false;
    };
  }, [api, productId, sort, ratingFilter]);

  const aggregate = data.aggregate || { count: 0, averageRating: 0 };
  const totalCount = Number(aggregate.count || 0);
  const avgRating = Number(aggregate.averageRating || 0);

  // Client-side safety filter to guarantee 100% strict product isolation
  const filteredReviews = (data.items || []).filter(
    (r) =>
      (!r.targetType || r.targetType === 'RETAIL_PRODUCT') &&
      (!r.targetReference || String(r.targetReference) === String(productId))
  );

  return (
    <section className="product-reviews-section" data-testid="product-reviews-section">
      <div className="product-reviews-head">
        <div>
          <span className="eyebrow" style={{ color: '#d97706', fontWeight: 600, letterSpacing: '0.05em' }}>
            ĐÁNH GIÁ TỪ KHÁCH HÀNG
          </span>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 700, margin: '0.25rem 0 0.5rem 0', color: '#0f172a' }}>
            Trải nghiệm về {productName ? `“${productName}”` : 'sản phẩm này'}
          </h2>
          <p className="muted" style={{ color: '#64748b', fontSize: '0.95rem' }}>
            Đánh giá thực tế từ các khách hàng đã mua và sở hữu sản phẩm này.
          </p>
        </div>

        <div className="product-reviews-score-card">
          <div className="product-score-number">
            {avgRating > 0 ? avgRating.toFixed(1) : '5.0'}
            <small style={{ fontSize: '1rem', color: '#94a3b8' }}> / 5</small>
          </div>
          <div className="product-score-meta">
            <span className="review-stars" style={{ color: '#f59e0b', fontSize: '1.25rem' }}>
              {formatStars(avgRating > 0 ? Math.round(avgRating) : 5)}
            </span>
            <span style={{ fontSize: '0.875rem', color: '#64748b' }}>
              {totalCount} đánh giá công khai
            </span>
          </div>
        </div>
      </div>

      {/* Control Bar: Sorting & Star Filter */}
      <div className="product-reviews-controls">
        <div className="sort-group">
          <label htmlFor="review-sort-select" style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155' }}>
            Sắp xếp theo:
          </label>
          <select
            id="review-sort-select"
            value={sort}
            onChange={(e) => setSort(e.target.value)}
            className="review-sort-select"
            data-testid="review-sort-select"
          >
            <option value="recent">Mới nhất (Recent reviews)</option>
            <option value="best_to_worse">Đánh giá cao nhất (Best to worse)</option>
            <option value="worse_to_best">Đánh giá thấp nhất (Worse to best)</option>
          </select>
        </div>

        <div className="filter-stars-pills" role="radiogroup" aria-label="Lọc theo số sao">
          <button
            type="button"
            className={`filter-pill ${ratingFilter === null ? 'active' : ''}`}
            onClick={() => setRatingFilter(null)}
            data-testid="filter-star-all"
          >
            Tất cả
          </button>
          {[5, 4, 3, 2, 1].map((star) => (
            <button
              key={star}
              type="button"
              className={`filter-pill ${ratingFilter === star ? 'active' : ''}`}
              onClick={() => setRatingFilter(ratingFilter === star ? null : star)}
              data-testid={`filter-star-${star}`}
            >
              {star} ★
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: '2.5rem' }}>
          <Loading />
        </div>
      )}

      {error && (
        <div className="review-error-box" style={{ background: '#fef2f2', color: '#b91c1c', padding: '1rem', borderRadius: '8px' }}>
          {error}
        </div>
      )}

      {!loading && !error && filteredReviews.length === 0 && (
        <div className="product-reviews-empty" data-testid="product-reviews-empty">
          <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>💬</div>
          <h3 style={{ fontSize: '1.15rem', color: '#334155', marginBottom: '0.25rem' }}>
            Chưa có đánh giá nào cho sản phẩm này
          </h3>
          <p style={{ color: '#64748b', fontSize: '0.9rem', maxWidth: '420px', margin: '0 auto' }}>
            Hãy là người đầu tiên trải nghiệm và chia sẻ cảm nhận chân thực về thiết kế này đến cộng đồng.
          </p>
        </div>
      )}

      {!loading && !error && filteredReviews.length > 0 && (
        <div className="product-reviews-list" data-testid="product-reviews-list">
          {filteredReviews.map((r) => (
            <ReviewCard key={r.id} review={r} />
          ))}
        </div>
      )}
    </section>
  );
}
