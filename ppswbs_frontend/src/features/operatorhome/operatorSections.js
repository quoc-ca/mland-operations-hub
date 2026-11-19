const item = (to, vi, en, descVi, descEn) => ({ to, vi, en, descVi, descEn });

// A Staff sees only the sessions a Manager assigned to them.
const mySessions = item('/operations/sessions', 'Ca của tôi', 'My sessions',
  'Các ca bạn được phân công: check-in khách, bắt đầu buổi, chốt điểm danh.', 'The sessions assigned to you: check guests in, start a session, finalize attendance.');

const dailyWork = {
  id: 'daily', vi: 'Việc hằng ngày', en: 'Daily work',
  items: [
    mySessions,
    item('/settlement', 'Hóa đơn quyết toán', 'Final bills',
      'Thêm add-on, thu phần còn lại, chốt hóa đơn.', 'Add add-ons, collect the balance, finalize the bill.'),
    item('/ota-connectivity/add-ons', 'Add-on tại xưởng (OTA)', 'Shop add-ons (OTA)',
      'Ghi nhận add-on cho khách đặt qua OTA.', 'Record add-ons for customers who booked through an OTA.'),
    item('/staff/retail-orders', 'Đơn bán lẻ chi nhánh', 'Branch retail orders',
      'Thu tiền mặt, giao hàng tại quầy, gửi hàng GHTK.', 'Collect counter cash, hand over pickups, ship with GHTK.'),
    item('/review-moderation', 'Quản lý đánh giá', 'Review moderation',
      'Kiểm duyệt và phản hồi đánh giá của khách hàng.', 'Moderate and reply to customer reviews.'),
  ],
};

const products = {
  id: 'products', vi: 'Sản phẩm', en: 'Products',
  items: [item('/catalogue-management/products', 'Sản phẩm', 'Products', 'Danh mục sản phẩm, import Excel.', 'Product catalogue and bulk import.')],
};

const account = {
  id: 'account', vi: 'Tài khoản', en: 'Account',
  items: [item('/profile', 'Hồ sơ cá nhân', 'Personal profile', 'Thông tin và mật khẩu của bạn.', 'Your details and password.')],
};

/**
 * The Staff home. The Manager home is its own page (feature 013, `features/managerhome`) and no longer a fixed menu.
 */
export function operatorSections() {
  return [dailyWork, products, account];
}
