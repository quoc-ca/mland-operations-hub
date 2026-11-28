import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router';
import { useRole } from '../../../shared/auth/useRole';
import { useT } from '../../../shared/i18n/I18nProvider';
import {
  ConfirmDialog,
  ErrorNotice,
  Field,
  Loading,
  SuccessNotice,
  useCommand,
  useResource,
} from '../../../shared/ui/components';
import { useDesignReviewApi } from '../api/useDesignReviewApi';
import DesignImage from '../components/DesignImage';
import Design3dDetailModal from '../components/Design3dDetailModal';
import { designReviewMessages } from '../messages';
import { LIMITS } from '../rules';

export default function ReviewDetailPage() {
  const { reviewRequestId } = useParams();
  const t = useT(designReviewMessages);
  const api = useDesignReviewApi();
  const command = useCommand();
  const role = useRole();
  const isManager = role === 'MANAGER';

  const [decision, setDecision] = useState('ACCEPTED');
  const [reason, setReason] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [formError, setFormError] = useState('');
  const [selectedPreviewImage, setSelectedPreviewImage] = useState(null);
  const [is3dModalOpen, setIs3dModalOpen] = useState(false);

  // Quote preparation state
  const [quoteAmount, setQuoteAmount] = useState('');
  const [quoteHours, setQuoteHours] = useState('72');
  const [quoteReason, setQuoteReason] = useState('');
  const [quoteSuccess, setQuoteSuccess] = useState('');
  const [quoteError, setQuoteError] = useState('');
  const [amountError, setAmountError] = useState('');
  const [hoursError, setHoursError] = useState('');
  const [quoteConfirmOpen, setQuoteConfirmOpen] = useState(false);
  const [quoteSubmitted, setQuoteSubmitted] = useState(false);
  const [quoteData, setQuoteData] = useState(null);
  const [managerApproveConfirmOpen, setManagerApproveConfirmOpen] = useState(false);
  const [managerRejectConfirmOpen, setManagerRejectConfirmOpen] = useState(false);
  const [managerRejectReason, setManagerRejectReason] = useState('');

  const { loading, error, data, reload, setData } = useResource(
    () => api.getDesignRequest(reviewRequestId),
    [reviewRequestId]
  );

  useEffect(() => {
    if (data?.quoteReference) {
      api.getCustomDesignQuote?.(reviewRequestId)
        .then(res => {
          if (res) setQuoteData(res);
        })
        .catch(() => { });
    }
  }, [data?.quoteReference, reviewRequestId, api]);

  const handleDigitOnlyKeyDown = (e) => {
    if (
      ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab', 'Enter', 'Home', 'End'].includes(e.key) ||
      (e.ctrlKey && ['a', 'c', 'v', 'x'].includes(e.key.toLowerCase()))
    ) {
      return;
    }
    if (!/^[0-9]$/.test(e.key)) {
      e.preventDefault();
    }
  };

  const handleAmountChange = (e) => {
    const raw = e.target.value.replace(/[^0-9]/g, '');
    setQuoteAmount(raw);
    if (formError) setFormError('');
    if (!raw) {
      setAmountError('Số tiền không được để trống.');
    } else if (Number(raw) > 1000000000) {
      setAmountError('Số tiền tối đa không được vượt quá 1.000.000.000 VNĐ.');
    } else if (Number(raw) <= 0) {
      setAmountError('Số tiền phải lớn hơn 0 VNĐ.');
    } else {
      setAmountError('');
    }
  };

  const handleHoursChange = (e) => {
    const raw = e.target.value.replace(/[^0-9]/g, '');
    setQuoteHours(raw);
    if (formError) setFormError('');
    if (!raw) {
      setHoursError('Thời hạn thanh toán không được để trống.');
    } else {
      const val = Number(raw);
      if (val < 24 || val > 72) {
        setHoursError('Thời hạn thanh toán chỉ được phép từ 24 đến 72 giờ.');
      } else {
        setHoursError('');
      }
    }
  };

  function handleOpenQuoteConfirm(e) {
    e.preventDefault();
    const amt = Number(quoteAmount);
    const hrs = Number(quoteHours);
    if (!quoteAmount || isNaN(amt) || amt <= 0 || amt > 1000000000) {
      setAmountError('Số tiền phải là số nguyên dương từ 1 đến 1.000.000.000 VNĐ.');
      setFormError('Vui lòng kiểm tra lại số tiền báo giá (tối đa 1.000.000.000 VNĐ).');
      return;
    }
    if (!quoteHours || isNaN(hrs) || hrs < 24 || hrs > 72) {
      setHoursError('Thời hạn thanh toán chỉ được phép từ 24 đến 72 giờ.');
      setFormError('Vui lòng kiểm tra lại thời gian (từ 24 đến 72 giờ).');
      return;
    }
    setAmountError('');
    setHoursError('');
    setFormError('');
    setQuoteError('');
    setQuoteConfirmOpen(true);
  }

  async function handleConfirmPrepareQuote() {
    if (command.pending) return;
    await command.run(async () => {
      try {
        const expiresAt = new Date(Date.now() + Number(quoteHours || 72) * 3600 * 1000).toISOString();
        const res = await api.prepareCustomDesignQuote(reviewRequestId, {
          totalAmount: quoteAmount,
          currency: 'VND',
          quoteExpiresAt: expiresAt,
          managerReason: quoteReason,
        });
        setQuoteConfirmOpen(false);
        setQuoteSubmitted(true);
        setQuoteData(res);
        setQuoteSuccess(`Gửi báo giá cho Quản lý (Manager) phê duyệt thành công! (Mã: ${res.quoteReference}, Tổng tiền: ${Number(res.totalAmount).toLocaleString('vi-VN')} đ)`);
        setQuoteError('');
        reload();
        return res;
      } catch (err) {
        setQuoteConfirmOpen(false);
        setQuoteError(err?.message || 'Gửi báo giá cho Quản lý thất bại. Vui lòng kiểm tra lại.');
        throw err;
      }
    });
  }

  async function handleConfirmManagerApprove() {
    if (command.pending) return;
    await command.run(async () => {
      try {
        const res = await api.approveCustomDesignQuoteByRequestId(reviewRequestId, {
          managerReason: 'Quản lý phê duyệt báo giá',
        });
        setManagerApproveConfirmOpen(false);
        setQuoteData(res);
        setQuoteSuccess(`Đã phê duyệt báo giá thành công! Đơn hàng đã chuyển sang trạng thái chờ Hội viên thanh toán 100%. (Mã: ${res.quoteReference})`);
        setQuoteError('');
        reload();
        return res;
      } catch (err) {
        setManagerApproveConfirmOpen(false);
        setQuoteError(err?.message || 'Phê duyệt báo giá thất bại. Vui lòng thử lại.');
        throw err;
      }
    });
  }

  async function handleConfirmManagerReject() {
    if (command.pending) return;
    const finalReason = managerRejectReason.trim() || 'Quản lý từ chối mẫu thiết kế và báo giá gia công.';
    await command.run(async () => {
      try {
        const res = await api.rejectCustomDesignQuoteByRequestId(reviewRequestId, {
          managerReason: finalReason,
        });
        setManagerRejectConfirmOpen(false);
        setQuoteData(res);
        setQuoteSuccess(`Đã từ chối mẫu thiết kế và hủy báo giá gia công thành công.`);
        setQuoteError('');
        reload();
        return res;
      } catch (err) {
        setManagerRejectConfirmOpen(false);
        setQuoteError(err?.message || 'Từ chối báo giá thất bại. Vui lòng thử lại.');
        throw err;
      }
    });
  }

  function handleOpenConfirm(e) {
    e.preventDefault();
    setFormError('');
    setAmountError('');
    setHoursError('');

    if (decision === 'ACCEPTED') {
      const amt = Number(quoteAmount);
      const hrs = Number(quoteHours);
      if (!quoteAmount || isNaN(amt) || amt <= 0 || amt > 1000000000) {
        setAmountError('Số tiền phải là số nguyên dương từ 1 đến 1.000.000.000 VNĐ.');
        setFormError('Vui lòng kiểm tra lại số tiền báo giá (tối đa 1.000.000.000 VNĐ).');
        return;
      }
      if (!quoteHours || isNaN(hrs) || hrs < 24 || hrs > 72) {
        setHoursError('Thời hạn thanh toán chỉ được phép từ 24 đến 72 giờ.');
        setFormError('Vui lòng kiểm tra lại thời gian (từ 24 đến 72 giờ).');
        return;
      }
      if (reason.trim() && [...reason.trim()].length > LIMITS.reasonMax) {
        setFormError(t('validation.reasonTooLong'));
        return;
      }
    } else if (decision === 'REJECTED') {
      if (!reason.trim()) {
        setFormError('Vui lòng nhập lý do từ chối thiết kế (bắt buộc).');
        return;
      }
      if ([...reason.trim()].length > LIMITS.reasonMax) {
        setFormError(t('validation.reasonTooLong'));
        return;
      }
    } else {
      if (!reason.trim()) {
        setFormError(t('validation.required'));
        return;
      }
      if ([...reason.trim()].length > LIMITS.reasonMax) {
        setFormError(t('validation.reasonTooLong'));
        return;
      }
    }

    setConfirmOpen(true);
  }

  async function handleConfirmDecision() {
    setConfirmOpen(false);
    await command.run(async () => {
      const effectiveReason = reason.trim() || (decision === 'ACCEPTED' ? 'Thiết kế đạt tiêu chuẩn chế tác, đã duyệt gửi báo giá cho khách hàng.' : '');
      const result = await api.recordReviewDecision(reviewRequestId, {
        decision,
        designVersion: data?.requestVersion || 1,
        explanation: effectiveReason,
        reason: effectiveReason,
      });

      if (decision === 'ACCEPTED') {
        try {
          const expiresAt = new Date(Date.now() + Number(quoteHours || 72) * 3600 * 1000).toISOString();
          const res = await api.prepareCustomDesignQuote(reviewRequestId, {
            totalAmount: quoteAmount,
            currency: 'VND',
            quoteExpiresAt: expiresAt,
            managerReason: quoteReason || effectiveReason,
          });
          setQuoteData(res);
          setQuoteSubmitted(true);
          setSuccessMsg(`Đã phê duyệt thiết kế và gửi báo giá cho Quản lý phê duyệt thành công! (Mã báo giá: ${res.quoteReference}, Tổng tiền: ${Number(res.totalAmount).toLocaleString('vi-VN')} đ)`);
        } catch (quoteErr) {
          setSuccessMsg('Đã chấp thuận yêu cầu thiết kế, nhưng gửi báo giá tự động gặp lỗi. Vui lòng kiểm tra báo giá.');
          setQuoteError(quoteErr?.message || 'Gửi báo giá thất bại.');
        }
      } else {
        setSuccessMsg(t('reviewDetail.success'));
      }

      setData(result);
      setReason('');
      reload();
      return result;
    });
  }

    if (loading) return <div className="design-container"><Loading /></div>;
    if (error) {
      return (
        <div className="design-container">
          <ErrorNotice error={error} messages={designReviewMessages} onReload={reload} />
        </div>
      );
    }
    if (!data) return null;

    const isTerminal = data.reviewStatus === 'ACCEPTED' || data.reviewStatus === 'REJECTED';
    const isAccepted = data.reviewStatus === 'ACCEPTED';
    const isRejected = data.reviewStatus === 'REJECTED';
    const explanation = data.decisionExplanation || data.safeExplanation || '';
    const images = Array.isArray(data.images) ? data.images : [];

    return (
      <div className="design-container">
        <header className="page-header">
          <h1>{t('reviewDetail.title', { id: data.id })}</h1>
          <p className="muted">
            {t('detail.reference', { ref: data.requestReference || data.id })} | {t('detail.version', { version: data.requestVersion || 1 })}
          </p>
        </header>

        <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
          <span className="badge-tag badge-status">
            {t('detail.statusLabel', { status: data.requestStatus || 'UNKNOWN' })}
          </span>
          <span className={`badge-tag ${isAccepted ? 'badge-success' : isRejected ? 'badge-danger' : 'badge-warning'}`}>
            {t('detail.reviewStatusLabel', { status: data.reviewStatus || 'NEED_REVIEW' })}
          </span>
          {data.ownershipType && (
            <span className="badge-tag badge-status">
              {data.ownershipType === 'MEMBER' ? 'Hội viên' : 'Khách vãng lai'}
            </span>
          )}
        </div>

        <SuccessNotice>{successMsg}</SuccessNotice>
        <ErrorNotice error={command.error} messages={designReviewMessages} onRetry={command.retry} onReload={reload} />

        {/* Already decided callout */}
        {isTerminal && (
          <div className="reviewer-feedback-box">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
              <span style={{ fontSize: '1.25rem' }}>{isAccepted ? '✅' : '❌'}</span>
              <h3 style={{ margin: 0, color: isAccepted ? '#166534' : '#991b1b' }}>
                Quyết định: {isAccepted ? 'Chấp thuận (ACCEPTED)' : 'Từ chối (REJECTED)'}
              </h3>
            </div>
            {explanation && (
              <div>
                <h4 style={{ margin: '0.75rem 0 0.25rem', fontSize: '0.9rem', color: '#475569' }}>
                  {t('detail.decisionExplanation')}:
                </h4>
                <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>{explanation}</p>
              </div>
            )}
          </div>
        )}

        {/* Quote Preparation section for Accepted Member Requests */}
        {isAccepted && (() => {
          const activeQuoteRef = quoteData?.quoteReference || data.quoteReference;
          const hasQuote = Boolean(quoteSubmitted || activeQuoteRef);
          const isQuoteApproved = Boolean(quoteData?.approvedByManager);
          const quoteGen = quoteData?.quoteGeneration;
          const displayAmount = quoteData?.totalAmount || quoteAmount;
          const displayReason = quoteData?.managerReason || quoteReason;
          const displayExpiry = quoteData?.expiresAt;

          return (
            <section className="card" style={{ marginBottom: '1.5rem', padding: '1.5rem', border: '1px solid #bfdbfe', borderRadius: '8px', background: '#eff6ff' }}>
              <h3 style={{ marginTop: 0, color: '#1e40af', fontSize: '1.2rem', marginBottom: '0.5rem' }}>
                Báo giá Gia công Tùy chỉnh (Custom Quote)
              </h3>
              <p style={{ fontSize: '0.9rem', color: '#3b82f6', marginBottom: '1rem' }}>
                Nhân viên nhập thông tin báo giá 100% để Quản lý (Manager) thẩm định và phê duyệt trước khi chuyển cho Hội viên thanh toán.
              </p>

              {quoteError && (
                <div className="alert-banner alert-banner-danger" style={{ marginBottom: '1rem', backgroundColor: '#fee2e2', color: '#991b1b', padding: '0.75rem 1rem', borderRadius: '6px', border: '1px solid #fca5a5' }}>
                  <p style={{ margin: 0 }}><strong>✕ Thất bại: </strong>{quoteError}</p>
                </div>
              )}

              {quoteSuccess && (
                <div className="alert-banner alert-banner-success" style={{ marginBottom: '1rem', backgroundColor: '#dcfce7', color: '#166534', padding: '0.75rem 1rem', borderRadius: '6px', border: '1px solid #86efac' }}>
                  <p style={{ margin: 0 }}><strong>✓ Thành công: </strong>{quoteSuccess}</p>
                </div>
              )}

              {hasQuote ? (
                <div style={{ background: '#ffffff', borderRadius: 6, border: '1px solid #dbeafe', padding: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                    <div>
                      <span style={{ fontSize: '0.85rem', color: '#64748b' }}>Mã báo giá: </span>
                      <strong style={{ fontSize: '1.05rem', color: '#1e40af' }}>{activeQuoteRef}</strong>
                      {quoteGen && <span className="muted small" style={{ marginLeft: '0.5rem' }}>(Lần #{quoteGen})</span>}
                    </div>
                    <div>
                      <span className={`badge-tag ${isQuoteApproved ? 'badge-success' : 'badge-warning'}`}>
                        {isQuoteApproved ? '✓ Quản lý đã duyệt' : '⏳ Chờ Quản lý (Manager) duyệt'}
                      </span>
                    </div>
                  </div>

                  {displayAmount && (
                    <div style={{ margin: '0.5rem 0' }}>
                      <span style={{ fontSize: '0.85rem', color: '#64748b', display: 'block' }}>Tổng tiền báo giá:</span>
                      <span style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a' }}>
                        {Number(displayAmount).toLocaleString('vi-VN')} đ
                      </span>
                    </div>
                  )}

                  {displayExpiry && (
                    <div style={{ fontSize: '0.85rem', color: '#64748b', marginBottom: '0.5rem' }}>
                      Hạn chót thanh toán: <strong>{new Date(displayExpiry).toLocaleString('vi-VN')}</strong>
                    </div>
                  )}

                  {displayReason && (
                    <div style={{ fontSize: '0.875rem', color: '#334155', background: '#f8fafc', padding: '0.5rem 0.75rem', borderRadius: 4, margin: '0.5rem 0' }}>
                      <strong>Ghi chú / Căn cứ tính giá:</strong> {displayReason}
                    </div>
                  )}

                  <p className="muted small" style={{ marginTop: '0.75rem', marginBottom: 0 }}>
                    {isQuoteApproved
                      ? 'Báo giá đã được Quản lý phê duyệt. Hội viên có thể vào xem và thanh toán toàn bộ 100% trong mục Đơn gia công của tôi.'
                      : 'Báo giá đã được gửi cho Quản lý phê duyệt. Nút gửi đã được ẩn để tránh gửi trùng lặp.'}
                  </p>

                  {isManager && !isQuoteApproved && (
                    <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px dashed #cbd5e1', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                      <button
                        type="button"
                        className="button button-primary"
                        onClick={() => setManagerApproveConfirmOpen(true)}
                        disabled={command.pending}
                        style={{ backgroundColor: '#16a34a', color: '#ffffff', padding: '0.5rem 1.25rem' }}
                      >
                        {command.pending ? 'Đang duyệt...' : 'Phê duyệt Báo giá (Manager Approve)'}
                      </button>
                      <button
                        type="button"
                        className="button button-primary"
                        onClick={() => setManagerRejectConfirmOpen(true)}
                        disabled={command.pending}
                        style={{ backgroundColor: '#dc2626', color: '#ffffff', padding: '0.5rem 1.25rem' }}
                      >
                        {command.pending ? 'Đang xử lý...' : 'Từ chối Thiết kế & Báo giá (Manager Reject)'}
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <form onSubmit={handleOpenQuoteConfirm} className="stack">
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
                    <div>
                      <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                        Tổng tiền báo giá (VNĐ, tối đa 1.000.000.000 đ) *
                      </label>
                      <input
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        data-testid="quote-amount-input"
                        value={quoteAmount}
                          onKeyDown = { handleDigitOnlyKeyDown }
                          onChange = { handleAmountChange }
                          placeholder = "Ví dụ: 800000 (tối đa 1.000.000.000 đ)"
                          required
                          style = {{
                            width: '100%',
                              padding: '0.5rem',
                      borderRadius: 6,
                      border: `1px solid ${amountError ? '#ef4444' : '#cbd5e1'}`,
                      }}
                    />
                      {amountError && (
                        <div data-testid="quote-amount-error" style={{ color: '#dc2626', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                          {amountError}
                        </div>
                      )}
                    </div>
                    <div>
                      <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                        Thời hạn thanh toán (24 - 72 Giờ) *
                      </label>
                      <input
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        data-testid="quote-hours-input"
                        value={quoteHours}
                        onKeyDown={handleDigitOnlyKeyDown}
                        onChange={handleHoursChange}
                        placeholder="Từ 24 đến 72 giờ"
                        required
                        style={{
                          width: '100%',
                          padding: '0.5rem',
                          borderRadius: 6,
                          border: `1px solid ${hoursError ? '#ef4444' : '#cbd5e1'}`,
                        }}
                      />
                      {hoursError && (
                        <div data-testid="quote-hours-error" style={{ color: '#dc2626', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                          {hoursError}
                        </div>
                      )}
                    </div>
                  </div>

                  <div style={{ marginTop: '0.75rem' }}>
                    <label style={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', marginBottom: '0.25rem' }}>
                      Ghi chú / Căn cứ tính giá cho Manager:
                    </label>
                    <textarea
                      rows={2}
                      value={quoteReason}
                      onChange={e => setQuoteReason(e.target.value)}
                      placeholder="Ghi chú cơ cấu vật liệu, đá quý đính kèm, thời gian gia công dự kiến..."
                      style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
                    />
                  </div>

                  {formError && (
                    <div style={{ color: '#dc2626', fontSize: '0.875rem', marginTop: '0.5rem' }}>
                      {formError}
                    </div>
                  )}

                  <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1rem' }}>
                    <button
                      type="submit"
                      className="button button-primary"
                      disabled={command.pending || !quoteAmount || !quoteHours || Boolean(amountError) || Boolean(hoursError)}
                      style={{ backgroundColor: '#1d4ed8', color: '#ffffff', padding: '0.5rem 1.25rem' }}
                    >
                      {command.pending ? 'Đang gửi...' : 'Gửi báo giá cho Manager duyệt'}
                    </button>
                  </div>
                </form>
              )}
            </section>
          );
        })()}

        {/* 3D Model & Technical Specs Section */}
        {Boolean(data.designSnapshot || data.designReference || data.catalogueReference) && (
          <section className="card" style={{ marginBottom: '1.5rem', padding: '1rem 1.25rem', border: '1px solid #bae6fd', borderRadius: '8px', background: '#f0f9ff', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <h4 style={{ margin: 0, color: '#0369a1', fontSize: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span>💍</span>
                <span>Bản vẽ thiết kế & Thông số kỹ thuật nhẫn</span>
              </h4>
              <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: '#0284c7' }}>
                Xem trực quan bản vẽ nhẫn, chi tiết chất liệu, đá chủ, size và khắc chữ của khách hàng.
              </p>
            </div>
            <button
              type="button"
              className="button button-primary"
              data-testid="view-3d-design-button"
              onClick={() => setIs3dModalOpen(true)}
              style={{ backgroundColor: '#0284c7', color: '#ffffff', padding: '0.5rem 1.25rem', fontWeight: 600, border: 'none', borderRadius: 6, cursor: 'pointer' }}
            >
              🔍 Xem chi tiết thiết kế & Thông số
            </button>
          </section>
        )}

        {/* Description */}
        {data.description && (
          <section className="card" style={{ marginBottom: '1.5rem', padding: '1.25rem', border: '1px solid #e2e8f0', borderRadius: '8px', background: '#ffffff' }}>
            <h4 style={{ marginTop: 0 }}>{t('workspace.descriptionLabel')}</h4>
            <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.description}</p>
            {data.catalogueReference && (
              <p className="muted small" style={{ marginTop: '0.5rem' }}>
                {t('workspace.catalogueRefLabel')}: <code>{data.catalogueReference}</code>
              </p>
            )}
          </section>
        )}

        {/* Uploaded Images for Review */}
        {images.length > 0 && (
          <section style={{ marginBottom: '1.5rem' }}>
            <h3>{t('detail.imagesGallery', { count: images.length })}</h3>
            <div className="image-upload-grid">
              {images.map((img, idx) => (
                <div
                  key={img.imageReference || idx}
                  className="image-upload-card"
                  style={{ cursor: 'pointer' }}
                  onClick={() => setSelectedPreviewImage(img)}
                >
                  <DesignImage
                    image={img}
                    alt={img.originalFilename || `Ảnh ${idx + 1}`}
                    className="image-upload-thumb"
                  />
                  <div className="image-upload-info">
                    <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={img.originalFilename}>
                      {img.originalFilename || `Ảnh ${idx + 1}`}
                    </span>
                    <span className="badge-tag badge-success" style={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}>
                      ✓ Đạt chất lượng
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Full-size image modal for reviewer */}
        {selectedPreviewImage && (
          <div
            role="dialog"
            aria-modal="true"
            style={{
              position: 'fixed',
              top: 0,
              left: 0,
              width: '100vw',
              height: '100vh',
              background: 'rgba(0, 0, 0, 0.75)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 9999,
              padding: '1rem',
            }}
            onClick={() => setSelectedPreviewImage(null)}
          >
            <div
              style={{
                background: '#ffffff',
                padding: '1rem',
                borderRadius: '8px',
                maxWidth: '90vw',
                maxHeight: '90vh',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
              }}
              onClick={e => e.stopPropagation()}
            >
              <DesignImage
                image={selectedPreviewImage}
                alt={selectedPreviewImage.originalFilename || 'Ảnh mẫu thiết kế'}
                style={{ maxWidth: '100%', maxHeight: '75vh', objectFit: 'contain' }}
              />
              <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
                <span className="small muted">{selectedPreviewImage.originalFilename}</span>
                <button type="button" className="button outline small" onClick={() => setSelectedPreviewImage(null)}>
                  Đóng
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Review Decision & Quote Form (Unified when not decided yet) */}
        {!isTerminal && (
          <section className="card" style={{ padding: '1.5rem', border: '1px solid #cbd5e1', borderRadius: '8px', background: '#f8fafc' }}>
            <h3 style={{ marginTop: 0, color: '#0f172a' }}>Thẩm định thiết kế & Báo giá gia công</h3>
            <p className="muted small" style={{ marginBottom: '1.25rem' }}>
              Nhân viên thẩm duyệt thiết kế, đồng thời tính toán số tiền báo giá để chuyển Quản lý (Manager) phê duyệt trước khi gửi khách hàng.
            </p>

            {/* Quick Action Decision Toggles */}
            <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className={`button ${decision === 'ACCEPTED' ? 'primary' : 'outline'}`}
                onClick={() => { setDecision('ACCEPTED'); setFormError(''); }}
                style={{
                  backgroundColor: decision === 'ACCEPTED' ? '#16a34a' : '#ffffff',
                  color: decision === 'ACCEPTED' ? '#ffffff' : '#166534',
                  borderColor: '#16a34a',
                  fontWeight: 600,
                  padding: '0.5rem 1rem',
                }}
              >
                ✓ Duyệt & Gửi báo giá
              </button>
              <button
                type="button"
                className={`button ${decision === 'REJECTED' ? 'primary danger' : 'outline'}`}
                onClick={() => { setDecision('REJECTED'); setFormError(''); }}
                style={{
                  backgroundColor: decision === 'REJECTED' ? '#dc2626' : '#ffffff',
                  color: decision === 'REJECTED' ? '#ffffff' : '#dc2626',
                  borderColor: '#dc2626',
                  fontWeight: 600,
                  padding: '0.5rem 1rem',
                }}
              >
                ✕ Từ chối thiết kế
              </button>
            </div>

            <form onSubmit={handleOpenConfirm} className="stack">
              <Field label={t('reviewDetail.decisionLabel')}>
                <select
                  value={decision}
                  onChange={e => {
                    setDecision(e.target.value);
                    setFormError('');
                  }}
                  disabled={command.pending}
                  style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
                >
                  <option value="ACCEPTED">✓ Duyệt & Gửi báo giá (ACCEPTED)</option>
                  <option value="REJECTED">✕ Từ chối thiết kế (REJECTED)</option>
                  <option value="CLARIFICATION_REQUIRED">❓ Yêu cầu làm rõ thông tin (CLARIFICATION_REQUIRED)</option>
                </select>
              </Field>

              {/* Merged Quote inputs when ACCEPTED is selected */}
              {decision === 'ACCEPTED' && (
                <div style={{ background: '#eff6ff', padding: '1rem', borderRadius: 8, border: '1px solid #bfdbfe', marginTop: '0.5rem', marginBottom: '0.5rem' }}>
                  <h4 style={{ margin: '0 0 0.75rem 0', color: '#1e40af', fontSize: '0.95rem' }}>
                    💰 Thông tin Báo giá (Chuyển Quản lý phê duyệt)
                  </h4>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
                    <div>
                      <label style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>
                        Tổng tiền báo giá (VNĐ, tối đa 1.000.000.000 đ) *
                      </label>
                      <input
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        data-testid="quote-amount-input"
                        value={quoteAmount}
                        onKeyDown={handleDigitOnlyKeyDown}
                        onChange={handleAmountChange}
                        placeholder="Ví dụ: 800000"
                        required={decision === 'ACCEPTED'}
                        style={{
                          width: '100%',
                          padding: '0.5rem',
                          borderRadius: 6,
                          border: `1px solid ${amountError ? '#ef4444' : '#cbd5e1'}`,
                          backgroundColor: '#ffffff',
                        }}
                      />
                      {amountError && (
                        <div data-testid="quote-amount-error" style={{ color: '#dc2626', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                          {amountError}
                        </div>
                      )}
                    </div>

                    <div>
                      <label style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>
                        Thời hạn thanh toán (24 - 72 Giờ) *
                      </label>
                      <input
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        data-testid="quote-hours-input"
                        value={quoteHours}
                        onKeyDown={handleDigitOnlyKeyDown}
                        onChange={handleHoursChange}
                        placeholder="Từ 24 đến 72 giờ"
                        required={decision === 'ACCEPTED'}
                        style={{
                          width: '100%',
                          padding: '0.5rem',
                          borderRadius: 6,
                          border: `1px solid ${hoursError ? '#ef4444' : '#cbd5e1'}`,
                          backgroundColor: '#ffffff',
                        }}
                      />
                      {hoursError && (
                        <div data-testid="quote-hours-error" style={{ color: '#dc2626', fontSize: '0.8rem', marginTop: '0.25rem' }}>
                          {hoursError}
                        </div>
                      )}
                    </div>
                  </div>

                  <div style={{ marginTop: '0.75rem' }}>
                    <label style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.25rem' }}>
                      Căn cứ tính giá cho Manager thẩm định (Tùy chọn):
                    </label>
                    <textarea
                      rows={2}
                      value={quoteReason}
                      onChange={e => setQuoteReason(e.target.value)}
                      placeholder="Chi tiết chi phí vàng bạc, đá chủ, công thợ chế tác, thời gian dự kiến..."
                      style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1', backgroundColor: '#ffffff' }}
                    />
                  </div>
                </div>
              )}

              <Field
                label={decision === 'ACCEPTED' ? 'Ghi chú / Nhận xét thẩm duyệt (Tùy chọn cho Chấp thuận)' : t('reviewDetail.reasonLabel')}
                hint={t('workspace.charCount', { count: [...reason].length })}
                error={formError || command.error?.fieldError?.('reason') || command.error?.fieldError?.('explanation')}
              >
                <textarea
                  value={reason}
                  onChange={e => {
                    setReason(e.target.value);
                    if (formError) setFormError('');
                  }}
                  placeholder={
                    decision === 'ACCEPTED'
                      ? 'Thiết kế đạt tiêu chuẩn chế tác, đã duyệt gửi báo giá cho khách hàng (Mặc định).'
                      : t('reviewDetail.reasonPlaceholder')
                  }
                  rows={decision === 'ACCEPTED' ? 2 : 4}
                  disabled={command.pending}
                  required={decision !== 'ACCEPTED'}
                />
              </Field>

              {formError && (
                <div style={{ color: '#dc2626', fontSize: '0.875rem', marginTop: '0.25rem' }}>
                  {formError}
                </div>
              )}

              <div className="actions" style={{ marginTop: '1rem', display: 'flex', justifyContent: 'flex-end' }}>
                <button
                  type="submit"
                  className={`button primary${decision === 'REJECTED' ? ' danger' : ''}`}
                  disabled={command.pending || (decision !== 'ACCEPTED' && !reason.trim()) || (decision === 'ACCEPTED' && (!quoteAmount || !quoteHours || Boolean(amountError) || Boolean(hoursError)))}
                  style={{
                    backgroundColor: decision === 'ACCEPTED' ? '#16a34a' : decision === 'REJECTED' ? '#dc2626' : '#d97706',
                    color: '#ffffff',
                    padding: '0.6rem 1.5rem',
                    fontWeight: 600,
                  }}
                >
                  {command.pending
                    ? 'Đang xử lý...'
                    : decision === 'ACCEPTED'
                    ? 'Duyệt gửi báo giá'
                    : decision === 'REJECTED'
                    ? 'Từ chối thiết kế'
                    : 'Yêu cầu làm rõ'}
                </button>
              </div>
            </form>
          </section>
        )}

        <ConfirmDialog
          open={confirmOpen}
          title={decision === 'ACCEPTED' ? 'Xác nhận duyệt thiết kế & gửi báo giá cho Quản lý' : t('reviewDetail.confirmTitle')}
          confirmLabel={command.pending ? 'Đang xử lý...' : decision === 'ACCEPTED' ? 'Duyệt gửi báo giá' : t('reviewDetail.submit')}
          onConfirm={handleConfirmDecision}
          onCancel={() => setConfirmOpen(false)}
          pending={command.pending}
          danger={decision === 'REJECTED'}
        >
          {decision === 'ACCEPTED' ? (
            <div>
              <p>Bạn có chắc chắn muốn duyệt mẫu thiết kế này và gửi báo giá cho Quản lý (Manager) phê duyệt không?</p>
              <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: 6, margin: '0.75rem 0', border: '1px solid #e2e8f0', fontSize: '0.9rem' }}>
                <div><strong>Tổng tiền báo giá:</strong> {Number(quoteAmount || 0).toLocaleString('vi-VN')} đ</div>
                <div><strong>Thời hạn thanh toán:</strong> {quoteHours || 72} giờ</div>
                {quoteReason && <div><strong>Căn cứ tính giá:</strong> <em>{quoteReason}</em></div>}
                {reason.trim() && <div><strong>Ghi chú thẩm duyệt:</strong> <em>{reason}</em></div>}
              </div>
            </div>
          ) : (
            <div>
              <p>{t('reviewDetail.confirmMessage', { decision: 'Từ chối' })}</p>
              <p><strong>Lý do giải thích:</strong> <em>{reason}</em></p>
            </div>
          )}
        </ConfirmDialog>

        {/* 3D Interactive Ring Model Modal */}
        <Design3dDetailModal
          isOpen={is3dModalOpen}
          onClose={() => setIs3dModalOpen(false)}
          designSnapshot={data.designSnapshot}
          designReference={data.designReference || data.catalogueReference || data.requestReference || data.id}
        />

        {/* Quote Preparation Confirmation Dialog */}
        <ConfirmDialog
          open={quoteConfirmOpen}
          title="Xác nhận gửi báo giá cho Quản lý"
          confirmLabel={command.pending ? 'Đang gửi...' : 'Xác nhận gửi'}
          onConfirm={handleConfirmPrepareQuote}
          onCancel={() => { if (!command.pending) setQuoteConfirmOpen(false); }}
          pending={command.pending}
        >
          <p>Bạn có chắc chắn muốn gửi báo giá này cho Quản lý (Manager) thẩm định và phê duyệt không?</p>
          <div style={{ background: '#f8fafc', padding: '0.75rem', borderRadius: 6, margin: '0.75rem 0', border: '1px solid #e2e8f0', fontSize: '0.9rem' }}>
            <div><strong>Tổng tiền báo giá:</strong> {Number(quoteAmount || 0).toLocaleString('vi-VN')} đ</div>
            <div><strong>Thời hạn thanh toán:</strong> {quoteHours || 72} giờ</div>
            {quoteReason && <div><strong>Ghi chú căn cứ:</strong> <em>{quoteReason}</em></div>}
          </div>
          <p className="muted small" style={{ margin: 0 }}>
            Sau khi xác nhận gửi, báo giá sẽ chuyển sang Quản lý và nút gửi sẽ biến mất để tránh gửi trùng lặp.
          </p>
        </ConfirmDialog>

        {/* Manager Approve Quote Dialog */}
        <ConfirmDialog
          open={managerApproveConfirmOpen}
          title="Xác nhận phê duyệt báo giá"
          confirmLabel={command.pending ? 'Đang phê duyệt...' : 'Phê duyệt báo giá'}
          onConfirm={handleConfirmManagerApprove}
          onCancel={() => { if (!command.pending) setManagerApproveConfirmOpen(false); }}
          pending={command.pending}
        >
          <p>Bạn có chắc chắn muốn phê duyệt báo giá này không?</p>
          <p className="muted small" style={{ margin: 0 }}>
            Sau khi duyệt, đơn hàng sẽ chuyển sang trạng thái sẵn sàng để Hội viên thanh toán toàn bộ 100%.
          </p>
        </ConfirmDialog>

        {/* Manager Reject Quote / Design Dialog */}
        <ConfirmDialog
          open={managerRejectConfirmOpen}
          title="Xác nhận từ chối mẫu thiết kế & báo giá"
          confirmLabel={command.pending ? 'Đang xử lý...' : 'Từ chối thiết kế & báo giá'}
          onConfirm={handleConfirmManagerReject}
          onCancel={() => { if (!command.pending) setManagerRejectConfirmOpen(false); }}
          pending={command.pending}
          danger={true}
        >
          <p>Bạn có chắc chắn muốn từ chối mẫu thiết kế và hủy báo giá gia công này không?</p>
          <div style={{ marginTop: '0.75rem', marginBottom: '0.75rem' }}>
            <label style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem', marginBottom: '0.35rem' }}>
              Lý do từ chối (bắt buộc):
            </label>
            <textarea
              rows={3}
              value={managerRejectReason}
              onChange={e => setManagerRejectReason(e.target.value)}
              placeholder="Nhập lý do không chấp thuận mẫu thiết kế / báo giá (độ khó quá cao, xưởng không đáp ứng, cấu hình rủi ro...)"
              style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1' }}
            />
          </div>
          <p className="muted small" style={{ margin: 0 }}>
            Sau khi từ chối, yêu cầu thiết kế sẽ chuyển thành REJECTED và đơn hàng sẽ bị hủy.
          </p>
        </ConfirmDialog>
      </div>
    );
  }
