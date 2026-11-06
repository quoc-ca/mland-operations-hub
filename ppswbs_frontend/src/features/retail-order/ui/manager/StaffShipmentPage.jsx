import React, { useState } from 'react';
import { RequireSession } from '../../../../shared/auth/RequireSession';
import { ErrorNotice, Loading, useResource } from '../../../../shared/ui/components';
import { useRetailApi } from '../../application/useRetailApi';

export const shipmentLabels = { PREPARING: 'Đang chuẩn bị hàng', HANDED_TO_CARRIER: 'Đã giao cho đơn vị vận chuyển', IN_TRANSIT: 'Đang giao hàng', DELIVERED: 'Giao thành công' };
export const nextShipmentStatus = { PREPARING: 'HANDED_TO_CARRIER', HANDED_TO_CARRIER: 'IN_TRANSIT', IN_TRANSIT: 'DELIVERED' };

export default function StaffShipmentPage() {
  return <main className="container public-page retail-page"><h1>Giao hàng GHTK</h1><p>Phí vận chuyển lấy từ GHTK. Vận đơn và các bước giao hàng bên dưới là mô phỏng; không tạo vận đơn thật.</p><RequireSession requiredText="Đăng nhập Staff hoặc Manager được phân công chi nhánh để quản lý giao hàng."><ShipmentList /></RequireSession></main>;
}

function ShipmentList() {
  const api = useRetailApi();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const result = useResource(() => api.listStaffShipments({ page, size: 20, status }), [page, status]);
  const open = async id => {
    setError(null); setSelected(null); setBusy(true);
    try { setSelected(await api.getStaffShipment(id)); } catch (e) { setError(e); } finally { setBusy(false); }
  };
  const advance = async () => {
    const next = nextShipmentStatus[selected.status];
    if (!next || busy) return;
    if (!window.confirm(`Xác nhận chuyển sang “${shipmentLabels[next]}”? Đây là thao tác mô phỏng do nhân viên ghi nhận.`)) return;
    setBusy(true); setError(null);
    try {
      await api.transitionStaffShipment(selected.id, { nextStatus: next, expectedVersion: selected.version });
      setSelected(await api.getStaffShipment(selected.id)); result.reload();
    } catch (e) { setError(e); } finally { setBusy(false); }
  };
  return <>
    <section className="workspace-card"><div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}><label>Trạng thái <select value={status} onChange={e => { setStatus(e.target.value); setPage(0); setSelected(null); }}><option value="">Tất cả</option>{Object.entries(shipmentLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><button className="button outline" onClick={result.reload}>Làm mới</button></div>
      {result.loading && <Loading />}{result.error && <ErrorNotice error={result.error} onRetry={result.reload} />}
      {!result.loading && !result.error && result.data?.items.length === 0 && <p>Chưa có vận đơn trong phạm vi chi nhánh và bộ lọc này. Chỉ đơn GHTK đã thanh toán mới xuất hiện.</p>}
      {result.data?.items.map(row => <article key={row.id} style={{ borderBottom: '1px solid #ddd', padding: '20px 0', overflowWrap: 'anywhere' }}><strong>Mô phỏng: {row.mockTrackingNumber}</strong><p>Đơn {row.branchOrderId} · Chi nhánh #{row.locationId}</p><p>{shipmentLabels[row.status]} · {row.recipientName || 'Chưa có tên người nhận'}</p><button className="button outline" disabled={busy} onClick={() => open(row.id)}>Xem và xử lý</button></article>)}
      <div style={{ display: 'flex', gap: 16, marginTop: 20 }}><button disabled={page === 0 || result.loading} onClick={() => setPage(p => p - 1)}>Trang trước</button><span>Trang {page + 1}</span><button disabled={result.loading || !result.data || page + 1 >= result.data.totalPages} onClick={() => setPage(p => p + 1)}>Trang sau</button></div>
    </section>
    {error && <ErrorNotice error={error} />}
    {selected && <section className="workspace-card" style={{ marginTop: 24, overflowWrap: 'anywhere' }}><h2>{selected.mockTrackingNumber} — vận đơn mô phỏng</h2><p>{selected.recipientName} · {selected.recipientPhone}</p><p>{[selected.recipientAddress, selected.wardName, selected.districtName, selected.provinceName].filter(Boolean).join(', ')}</p><h3>{shipmentLabels[selected.status]}</h3>{nextShipmentStatus[selected.status] ? <button className="button primary" disabled={busy} onClick={advance}>{busy ? 'Đang lưu…' : `Xác nhận: ${shipmentLabels[nextShipmentStatus[selected.status]]}`}</button> : <p>Đã giao thành công. Luồng giao hàng kết thúc, không thể đổi trạng thái thêm.</p>}<button className="button outline" disabled={busy} onClick={() => open(selected.id)}>Tải lại chi tiết</button><h3>Lịch sử xử lý</h3>{selected.history.map((entry, index) => <p key={index}>{shipmentLabels[entry.status]} · {entry.actorLabel} · {entry.createdAt ? new Date(entry.createdAt).toLocaleString('vi-VN') : '—'}</p>)}</section>}
  </>;
}
