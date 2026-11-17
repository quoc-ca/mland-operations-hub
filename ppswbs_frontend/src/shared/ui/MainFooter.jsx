import React from 'react';
import { Link } from 'react-router';
import './footer.css';

/**
 * Shared Footer across Landing Page and Application Features
 * Follows Figma Page 01 · Public & Guest (Node 521:229 Desktop & 521:318 Mobile)
 */
export default function MainFooter() {
  return (
    <footer className="main-footer" role="contentinfo">
      <div className="main-footer-container">
        <div className="main-footer-columns">
          {/* Column 1: Brand Info */}
          <div className="main-footer-col brand-col">
            <h4 className="main-footer-brand-title">MLAND JEWELRY &amp; WORKSHOP</h4>
            <p className="main-footer-brand-desc">
              Hệ thống Đặt lịch Workshop Kim hoàn Thủ công &amp; Bán Trang sức Thiết kế Cá nhân hoá (PPS-WBS).
              Nơi bạn tự tay tạo nên câu chuyện của riêng mình qua từng nét chạm bạc.
            </p>
          </div>

          {/* Column 2: Services */}
          <div className="main-footer-col">
            <h5 className="main-footer-heading">DỊCH VỤ &amp; WORKSHOP</h5>
            <ul className="main-footer-list">
              <li><Link to="/workshops">Workshop Nhẫn Bạc (2h)</Link></li>
              <li><Link to="/workshops">Workshop Nhẫn Cặp (3.5h)</Link></li>
              <li><Link to="/workshops">Masterclass Nghệ Nhân</Link></li>
              <li><Link to="/workshops/PKG-COUPLE-SILVER/design">Đặt Thiết Kế Theo Ý Tưởng</Link></li>
              <li><Link to="/products">Bộ Sưu Tập Trang Sức</Link></li>
            </ul>
          </div>

          {/* Column 3: Support & Policy */}
          <div className="main-footer-col">
            <h5 className="main-footer-heading">HỖ TRỢ &amp; CHÍNH SÁCH</h5>
            <ul className="main-footer-list">
              <li><Link to="/workshop-booking/lookup">Tra Cứu Lịch Hẹn Đã Đặt</Link></li>
              <li><span>Bảo Hành Bạc 925 Trọn Đời</span></li>
              <li><span>Đánh Bóng &amp; Làm Mới Free</span></li>
              <li><Link to="/ai-support">Hỗ Trợ Tư Vấn Kiểu Dáng AI</Link></li>
              <li><span>Điều Khoản &amp; Bảo Mật</span></li>
            </ul>
          </div>

          {/* Column 4: Branches */}
          <div className="main-footer-col branches-col">
            <h5 className="main-footer-heading">HỆ THỐNG CHI NHÁNH</h5>
            <div className="main-footer-branch-item">
              <p className="branch-title">Chi nhánh Đà Nẵng:</p>
              <p className="branch-detail">45 Bạch Đằng, Q. Hải Châu</p>
              <p className="branch-hotline">Hotline: 0905.123.xxx</p>
            </div>
            <div className="main-footer-branch-item">
              <p className="branch-title">Chi nhánh TP.HCM:</p>
              <p className="branch-detail">123 Nguyễn Huệ, Quận 1</p>
              <p className="branch-hotline">Hotline: 0908.456.xxx</p>
            </div>
          </div>
        </div>

        <div className="main-footer-divider" />

        <div className="main-footer-bottom">
          <p className="main-footer-copyright">
            © 2026 M-Land Operations Hub (SEP490_G22). All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
