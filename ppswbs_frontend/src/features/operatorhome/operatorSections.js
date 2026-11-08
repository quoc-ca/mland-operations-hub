const item = (to, vi, en, descVi, descEn) => ({ to, vi, en, descVi, descEn });

const sessionBoard = item('/operations/sessions', 'Bảng điều phối ca', 'Workshop session board',
  'Check-in khách, bắt đầu buổi, chốt điểm danh.', 'Check guests in, start a session, finalize attendance.');
// A Staff sees only the sessions a Manager assigned to them.
const mySessions = item('/operations/sessions', 'Ca của tôi', 'My sessions',
  'Các ca bạn được phân công: check-in khách, bắt đầu buổi, chốt điểm danh.', 'The sessions assigned to you: check guests in, start a session, finalize attendance.');

const dailyWork = {
  id: 'daily', vi: 'Việc hằng ngày', en: 'Daily work',
  items: [
    sessionBoard,
    item('/settlement', 'Hóa đơn quyết toán', 'Final bills',
      'Thêm add-on, thu phần còn lại, chốt hóa đơn.', 'Add add-ons, collect the balance, finalize the bill.'),
    item('/ota-connectivity/add-ons', 'Add-on tại xưởng (OTA)', 'Shop add-ons (OTA)',
      'Ghi nhận add-on cho khách đặt qua OTA.', 'Record add-ons for customers who booked through an OTA.'),
  ],
};

const managerOnly = [
  {
    id: 'workshop', vi: 'Quản lý workshop', en: 'Workshop management',
    items: [
      item('/operations/packages', 'Gói workshop', 'Workshop packages', 'Tạo, xuất bản và ngừng bán gói.', 'Create, publish and retire packages.'),
      item('/operations/package-add-ons', 'Add-on gói', 'Package add-ons', 'Danh mục add-on và giá của từng gói.', 'Add-on catalogue and prices per package.'),
      item('/operations/admin', 'Chi nhánh & khung giờ', 'Locations & slots', 'Cơ sở, khung giờ và sinh buổi hàng loạt.', 'Locations, slots and bulk session generation.'),
    ],
  },
  {
    id: 'settlement', vi: 'Quyết toán & hoàn tiền', en: 'Settlement & refunds',
    items: [
      item('/settlement/cases', 'Sự vụ & bồi hoàn', 'Cases & remedies', 'Hủy, bồi hoàn và điều chỉnh folio.', 'Cancellations, remedies and folio corrections.'),
      item('/settlement/cases?tab=policies', 'Chính sách hoàn tiền', 'Refund policies', 'Các phiên bản chính sách hoàn tiền.', 'Refund policy revisions.'),
    ],
  },
  {
    id: 'ota', vi: 'Kết nối OTA', en: 'OTA connectivity',
    items: [
      item('/ota-connectivity/channels', 'Kênh đối tác', 'Channels', 'Klook, GetYourGuide và trạng thái kết nối.', 'Klook, GetYourGuide and connection status.'),
      item('/ota-connectivity/reconciliation', 'Đối soát đồng bộ', 'Reconciliation queue', 'Hàng đợi đối soát đơn OTA.', 'OTA booking reconciliation queue.'),
      item('/ota-connectivity/finance', 'Bảng chứng tài chính', 'Finance evidence', 'Chứng từ tài chính của kênh OTA.', 'Financial evidence of OTA channels.'),
    ],
  },
  {
    id: 'commerce', vi: 'Sản phẩm & ưu đãi', en: 'Products & promotions',
    items: [
      item('/catalogue-management/products', 'Sản phẩm', 'Products', 'Danh mục sản phẩm, import Excel.', 'Product catalogue and bulk import.'),
      item('/promotion-management/programs', 'Chương trình ưu đãi', 'Promotion programs', 'Voucher, chương trình và chính sách điểm.', 'Vouchers, programs and point policy.'),
    ],
  },
];

const products = {
  id: 'products', vi: 'Sản phẩm', en: 'Products',
  items: [item('/catalogue-management/products', 'Sản phẩm', 'Products', 'Danh mục sản phẩm, import Excel.', 'Product catalogue and bulk import.')],
};

const account = {
  id: 'account', vi: 'Tài khoản', en: 'Account',
  items: [item('/profile', 'Hồ sơ cá nhân', 'Personal profile', 'Thông tin và mật khẩu của bạn.', 'Your details and password.')],
};

/**
 * Staff and Manager both manage products. A Manager also sees management of workshops, settlement, OTA and promotions;
 * their Products and Promotions share one section.
 */
export function operatorSections(role) {
  if (role === 'MANAGER') return [dailyWork, ...managerOnly, account];
  const staffDaily = { ...dailyWork, items: [mySessions, ...dailyWork.items.slice(1)] };
  return [staffDaily, products, account];
}
