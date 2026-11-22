import { useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { useAuth } from '../../shared/auth/AuthProvider';
import { useHttp } from '../../shared/api/useHttp';
import { Loading } from '../../shared/ui/components';
import OperationsAuthWorkspace from '../../shared/ui/OperationsAuthWorkspace';
import { createAuditReviewApi } from '../api/auditReviewApi';
import AuditFilterBar from '../components/AuditFilterBar';
import { OperationsRows, SettlementRows } from '../components/AuditResults';
import {
  activeFilterCount, emptyFilters, filtersKey, readFilters, tabOf, toQuery, writeFilters,
} from '../components/auditReviewModel';
import { useAuditFeed } from '../components/useAuditFeed';
import '../../ota-connectivity/ota-connectivity.css';
import '../workshop-operations.css';

const TAB_LABELS = { operations: 'Vận hành', settlement: 'Quyết toán' };

function Shell({ children }) {
  return (
    <div className="ota-workspace">
      
      <main className="ota-main">
        <div className="ota-topline">MLAND / WORKSHOP OPERATIONS <span>● Backend-authorized</span></div>
        {children}
      </main>
    </div>
  );
}

/** One tab: its own filters (in the URL), its own keyset-paged list, and the loading / empty / error states. */
function AuditTab({ tab, api, applied, onApply }) {
  const query = useMemo(() => toQuery(applied), [applied]);
  const read = tab === 'operations' ? api.readOperationsEvents : api.readSettlementRecords;
  const feed = useAuditFeed({
    load: cursor => read({ ...query, cursor: cursor || undefined }),
    queryKey: `${tab}:${filtersKey(applied)}`,
    enabled: true,
  });
  const filtered = activeFilterCount(applied) > 0;
  const Rows = tab === 'operations' ? OperationsRows : SettlementRows;
  const retry = feed.rows.length > 0 ? feed.loadMore : feed.reload;

  return (
    <div role="tabpanel" aria-label={TAB_LABELS[tab]}>
      <AuditFilterBar key={filtersKey(applied)} tab={tab} applied={applied} disabled={feed.loading} onApply={onApply} />
      {!applied.from && <p className="audit-note">Đang hiển thị 7 ngày gần nhất vì chưa chọn “Từ”.</p>}

      {feed.error && (
        <div className="audit-error" role="alert">
          <span>
            Không tải được nhật ký.
            {feed.error.correlationId && <> Mã tham chiếu: <span className="mono">{feed.error.correlationId}</span></>}
          </span>
          <button type="button" className="button outline" onClick={retry}>Thử lại</button>
        </div>
      )}

      {feed.loading && (
        <div aria-busy="true">
          <div className="audit-skeleton" /><div className="audit-skeleton" /><div className="audit-skeleton" />
          <p className="audit-note" role="status">Đang tải nhật ký…</p>
        </div>
      )}

      {feed.loaded && !feed.loading && !feed.error && feed.rows.length === 0 && (
        <div className="audit-empty">
          <b>Không có dòng audit nào khớp bộ lọc</b>
          Thử bỏ bớt bộ lọc hoặc mở rộng khoảng thời gian.
          {filtered && (
            <div><button type="button" className="button outline" onClick={() => onApply(emptyFilters(tab))}>Xóa bộ lọc</button></div>
          )}
        </div>
      )}

      {feed.rows.length > 0 && (
        <>
          <div className="audit-table-wrap"><Rows rows={feed.rows} /></div>
          {feed.nextCursor && (
            <div className="audit-more">
              <button type="button" className="button outline" onClick={feed.loadMore} disabled={feed.loadingMore}>
                {feed.loadingMore ? 'Đang tải…' : 'Tải thêm'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

/**
 * 003.2 US7 (FR-047 to FR-055): the Manager's read-only audit screen, two tabs. The page only reads; the backend
 * decides every permission (a 403 is the shared access-denied routing, and a lost session returns here through the
 * URL, which holds the tab and both tabs' filters).
 */
export default function AuditReviewPage() {
  const { status } = useAuth();
  const http = useHttp();
  const api = useMemo(() => createAuditReviewApi(http), [http]);
  const [params, setParams] = useSearchParams();
  const tab = tabOf(params);
  const applied = useMemo(() => readFilters(params, tab), [params, tab]);

  const selectTab = next => {
    if (next === tab) return;
    const updated = new URLSearchParams(params);
    if (next === 'settlement') updated.set('tab', 'settlement'); else updated.delete('tab');
    setParams(updated);
  };
  const apply = filters => setParams(writeFilters(params, tab, filters));

  if (status === 'loading') return <Loading />;
  if (status !== 'signed-in') {
    return (
      <OperationsAuthWorkspace
        topline="MLAND / WORKSHOP OPERATIONS"
        message="Yêu cầu đăng nhập để xem nhật ký audit workshop."
      />
    );
  }

  return (
    <Shell>
      <header className="ota-page-heading">
        <div>
          <p>Chỉ đọc. Dòng mới nhất ở trên.{tab === 'operations' ? ' Việc xem nhật ký không được ghi lại.' : ''}</p>
        </div>
      </header>
      <section className="ota-panel">
        <div className="audit-tabs" role="tablist" aria-label="Nguồn audit">
          {Object.keys(TAB_LABELS).map(key => (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={tab === key}
              className={tab === key ? 'on' : undefined}
              onClick={() => selectTab(key)}
            >
              {TAB_LABELS[key]}
            </button>
          ))}
        </div>
        <AuditTab key={tab} tab={tab} api={api} applied={applied} onApply={apply} />
      </section>
    </Shell>
  );
}
