import React from 'react';
import { useSearchParams } from 'react-router';
import { useAuth } from '../../../shared/auth/AuthProvider';
import { useRole } from '../../../shared/auth/useRole';
import MyReviewsPage from './MyReviewsPage';
import PublicReviewListPage from './PublicReviewListPage';

/**
 * /reviews (session 2026-11-23): a signed-in Member gets their own "Chưa đánh giá / Đã đánh giá" page. Everyone else
 * (Guest with the email/phone lookup, Staff, Manager) and a package link (/reviews?packageId=…, from a workshop
 * package page) keep the public list.
 */
export default function ReviewsHomePage() {
  const { status } = useAuth();
  const role = useRole();
  const [params] = useSearchParams();
  const isMember = status === 'signed-in' && role === 'MEMBER';
  if (isMember && !params.get('packageId')) return <MyReviewsPage />;
  return <PublicReviewListPage />;
}
