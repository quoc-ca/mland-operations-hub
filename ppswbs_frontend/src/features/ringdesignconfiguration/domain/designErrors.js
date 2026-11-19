// Mapping for typed backend error codes to localized error messages

export function getErrorMessage(error, defaultMessage = 'Đã xảy ra lỗi không xác định.') {
  if (!error) return defaultMessage;

  const code = error.code || '';
  switch (code) {
    case 'DESIGN_INPUT_INVALID':
      return 'Thông tin cấu hình nhẫn không hợp lệ. Vui lòng kiểm tra lại các trường nhập liệu.';
    case 'DESIGN_OPTION_INVALID':
      return error.details && error.details.length > 0
        ? `Một hoặc nhiều tùy chọn đã chọn không còn khả dụng hoặc không hợp lệ: ${error.details.join(', ')}`
        : 'Một hoặc nhiều tùy chọn đã chọn không còn khả dụng hoặc không hợp lệ.';
    case 'GEMSTONE_TOTAL_LIMIT_EXCEEDED':
      return 'Tổng số lượng đá quý không được vượt quá 10 viên.';
    case 'TEXT_ENGRAVING_LIMIT_EXCEEDED':
      return 'Số lượng mục khắc chữ tối đa là 2.';
    case 'SYMBOL_ENGRAVING_LIMIT_EXCEEDED':
      return 'Số lượng mục khắc biểu tượng tối đa là 2.';
    case 'IMAGE_ENGRAVING_LIMIT_EXCEEDED':
      return 'Số lượng ảnh khắc laser tối đa là 2.';
    case 'IMAGE_PREPROCESSING_INVALID':
      return 'Thông số tiền xử lý ảnh không hợp lệ (độ tương phản và độ sắc nét từ 0 đến 100).';
    case 'ENGRAVING_PREVIEW_UNAVAILABLE':
      return 'Không thể tạo bản xem trước khắc laser. Vui lòng thử lại với ảnh khác.';
    case 'DESIGN_PACKAGE_INCOMPATIBLE':
      return 'Tùy chọn không tương thích với gói workshop đã chọn.';
    case 'DESIGN_BRANCH_INCOMPATIBLE':
      return 'Chi nhánh workshop được chọn chưa hỗ trợ một số vật liệu hoặc chi tiết của mẫu nhẫn này.';
    case 'DESIGN_SESSION_EXPIRED':
      return 'Phiên làm việc của bạn đã hết hạn sau 20 phút không hoạt động. Vui lòng bắt đầu lại để tiếp tục.';
    case 'DESIGN_ACCESS_DENIED':
      return 'Bạn không có quyền truy cập vào mẫu thiết kế nhẫn này.';
    case 'DESIGN_VERSION_CONFLICT':
      return 'Mẫu thiết kế đã được cập nhật từ một phiên làm việc khác. Dữ liệu đang được tải lại.';
    case 'DESIGN_LOCKED':
      return 'Mẫu thiết kế đã được gắn vào lịch đặt workshop và đã bị khóa, không thể chỉnh sửa.';
    case 'IDEMPOTENCY_CONFLICT':
      return 'Yêu cầu trùng lặp nhưng nội dung đã bị thay đổi.';
    case 'DESIGN_RULES_UNAVAILABLE':
      return 'Hệ thống định giá và thẩm định quy tắc tạm thời không khả dụng. Vui lòng thử lại sau.';
    case 'AUTHENTICATION_REQUIRED':
      return 'Vui lòng đăng nhập tài khoản hội viên để thực hiện thao tác này.';
    default:
      return error.message || defaultMessage;
  }
}

