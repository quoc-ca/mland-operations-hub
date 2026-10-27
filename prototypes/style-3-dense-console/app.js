/**
 * PPSWBS Prototype - Style 3: Operations Hub (High-Density Console)
 * Features 001 (Account Management & Profile) & 008 (Retail Order & Fulfillment)
 * Implementation adhering strictly to ppswbs-spec/DESIGN.md & ppswbs_frontend/AGENT.md
 */

(function () {
  'use strict';

  // Localization Dictionary (VI / EN)
  const i18n = {
    vi: {
      brandTag: "Operations Hub",
      menuCatalogue: "Kho Hàng Retail",
      menuOrders: "Đơn Hàng & Vận Đơn GHTK",
      menuProfile: "Hồ Sơ Thành Viên (FE-01)",
      menuStaff: "Bàn Đóng Gói Chi Nhánh",
      roleCustomer: "Góc nhìn Khách hàng",
      roleStaff: "Góc nhìn Nhân viên (Staff)",
      branchLabel: "Chi nhánh:",
      cartTitle: "Đơn Mua Sắm Hiện Tại",
      emptyCart: "Chưa có sản phẩm nào trong giỏ hàng.",
      addToCart: "Thêm giỏ",
      outOfStock: "Hết hàng",
      stockAvailable: "Tồn kho",
      ringSize: "Size:",
      splitNotice: "Hệ thống tự động gom đơn & tách kiện GHTK theo từng chi nhánh tương ứng.",
      branchSubtotal: "Tiền hàng:",
      shippingGhtk: "Cước phí GHTK:",
      totalPayable: "Tổng thanh toán:",
      checkoutBtn: "Xác Nhận & Thanh Toán VNPay",
      customerInfoTitle: "Thông tin thành viên (FE-01)",
      fullName: "Họ và tên:",
      phone: "Số điện thoại:",
      email: "Email:",
      verified: "Đã xác thực",
      address: "Địa chỉ giao hàng:",
      saveProfile: "Cập Nhật Hồ Sơ",
      profileSaved: "Đã cập nhật hồ sơ thành công!",
      paymentSuccess: "Thanh toán thành công! Mã đơn hàng và kiện GHTK đã được tạo.",
      orderPlaced: "Đã đặt hàng",
      orderPaid: "Đã thanh toán",
      orderPacking: "Đang đóng gói",
      orderDispatched: "Đã giao GHTK",
      orderInTransit: "Đang giao",
      orderDelivered: "Hoàn tất",
      carrierCode: "Mã GHTK:",
      staffStartPacking: "Bắt đầu đóng gói",
      staffDispatch: "Bàn giao bưu cục GHTK",
      backToHub: "← Trở về Hub",
    },
    en: {
      brandTag: "Operations Hub",
      menuCatalogue: "Retail Inventory",
      menuOrders: "Orders & GHTK Shipments",
      menuProfile: "Member Profile (FE-01)",
      menuStaff: "Branch Packing Station",
      roleCustomer: "Customer View",
      roleStaff: "Staff Operator View",
      branchLabel: "Branch:",
      cartTitle: "Current Shopping Cart",
      emptyCart: "No items in shopping cart.",
      addToCart: "Add",
      outOfStock: "Out of Stock",
      stockAvailable: "Stock",
      ringSize: "Size:",
      splitNotice: "Orders from multiple branches are automatically separated into individual GHTK shipments.",
      branchSubtotal: "Subtotal:",
      shippingGhtk: "GHTK Freight:",
      totalPayable: "Combined Total:",
      checkoutBtn: "Confirm & VNPay Checkout",
      customerInfoTitle: "Member Info (FE-01)",
      fullName: "Full Name:",
      phone: "Phone Number:",
      email: "Email:",
      verified: "Verified",
      address: "Delivery Address:",
      saveProfile: "Update Profile",
      profileSaved: "Profile updated successfully!",
      paymentSuccess: "Payment confirmed! Branch orders & GHTK parcels scheduled.",
      orderPlaced: "Order Placed",
      orderPaid: "Paid",
      orderPacking: "Packing",
      orderDispatched: "Dispatched",
      orderInTransit: "In Transit",
      orderDelivered: "Delivered",
      carrierCode: "GHTK Code:",
      staffStartPacking: "Start Packing",
      staffDispatch: "Dispatch to GHTK",
      backToHub: "← Back to Hub",
    }
  };

  const branches = [
    { id: "BRANCH_HN", nameVi: "Hà Nội Atelier (42 Nhà Chung)", nameEn: "Hanoi Atelier (42 Nha Chung)" },
    { id: "BRANCH_SG", nameVi: "Sài Gòn Craft Lab (150 Pasteur)", nameEn: "Saigon Craft Lab (150 Pasteur)" }
  ];

  const productsCatalogue = [
    {
      id: "P01",
      sku: "RNG-S925-MIN",
      nameVi: "Nhẫn Bạc S925 Minimalist Band",
      nameEn: "Minimalist S925 Silver Band",
      price: 650000,
      weightGrams: 15,
      dimensions: "5x5x3 cm",
      sizes: [9, 11, 13, 15],
      stock: {
        BRANCH_HN: 5,
        BRANCH_SG: 8
      }
    },
    {
      id: "P02",
      sku: "RNG-MOIS-SOL",
      nameVi: "Nhẫn Kim Cương Moissanite Atelier",
      nameEn: "Atelier Moissanite Solitaire Ring",
      price: 2450000,
      weightGrams: 25,
      dimensions: "6x6x4 cm",
      sizes: [10, 12, 14],
      stock: {
        BRANCH_HN: 3,
        BRANCH_SG: 4
      }
    },
    {
      id: "P03",
      sku: "RNG-CRAFT-ENG",
      nameVi: "Nhẫn Khắc Chữ Thủ Công Mland",
      nameEn: "Hand-Engraved Mland Craft Ring",
      price: 850000,
      weightGrams: 20,
      dimensions: "5x5x3 cm",
      sizes: [12, 14, 16],
      stock: {
        BRANCH_HN: 10,
        BRANCH_SG: 0
      }
    }
  ];

  const STATE_KEY = "MLAND_PROTOTYPE_STATE_V1";
  let appState = {
    lang: "vi",
    role: "STAFF", // Defaults to STAFF for Style 3 Console view
    selectedBranch: "BRANCH_HN",
    activeTab: "staffQueue", // staffQueue | catalogue | orders | profile
    selectedSizes: { P01: 11, P02: 12, P03: 14 },
    cart: [],
    profile: {
      fullName: "Lê Minh Quân",
      email: "quan.lm@mland.vn",
      phone: "0912 345 678",
      isEmailVerified: true,
      address: "12A Phố Tràng Tiền, Phường Tràng Tiền, Quận Hoàn Kiếm, Hà Nội"
    },
    orders: [
      {
        orderId: "ML-HN-9021",
        branchId: "BRANCH_HN",
        createdAt: "2026-10-25 14:30",
        items: [
          { nameVi: "Nhẫn Bạc S925 Minimalist Band", nameEn: "Minimalist S925 Silver Band", size: 11, quantity: 1, price: 650000 }
        ],
        subtotal: 650000,
        shippingFee: 32000,
        total: 682000,
        status: "DISPATCHED_GHTK",
        carrierCode: "GHTK-HN-884102",
        fulfilment: "DELIVERY_GHTK",
        recipient: {
          name: "Lê Minh Quân",
          phone: "0912 345 678",
          address: "12A Phố Tràng Tiền, Hoàn Kiếm, Hà Nội"
        }
      }
    ]
  };

  try {
    const saved = localStorage.getItem(STATE_KEY);
    if (saved) {
      appState = { ...appState, ...JSON.parse(saved) };
    }
  } catch (e) {}

  function saveState() {
    try {
      localStorage.setItem(STATE_KEY, JSON.stringify(appState));
    } catch (e) {}
  }

  function formatCurrency(amount) {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  }

  function showToast(message) {
    const container = document.getElementById("toastContainer");
    if (!container) return;
    const toast = document.createElement("div");
    toast.className = "toast";
    toast.innerHTML = `<span>⚡</span> <span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(8px)';
      setTimeout(() => toast.remove(), 200);
    }, 2800);
  }

  function renderApp() {
    const t = i18n[appState.lang];
    const appRoot = document.getElementById("app");
    if (!appRoot) return;

    const totalCartCount = appState.cart.reduce((sum, item) => sum + item.quantity, 0);

    appRoot.innerHTML = `
      <!-- Console Sidebar -->
      <aside class="console-sidebar">
        <div class="sidebar-header">
          <a href="../index.html" class="sidebar-brand">
            <span>MLAND</span>
            <span class="pill">CONSOLE</span>
          </a>
          <a href="../index.html" class="btn-secondary" style="font-size: 11px; padding: 2px 6px; background: transparent; color: #94A3B8; border-color: #334155;">
            ${t.backToHub}
          </a>
        </div>

        <ul class="sidebar-menu">
          <li class="sidebar-item-header">Vận Hành & Đơn Hàng (FE-08)</li>
          <li>
            <button class="sidebar-btn ${appState.activeTab === 'staffQueue' ? 'active' : ''}" data-tab="staffQueue">
              <span>📦</span>
              <span>${t.menuStaff}</span>
            </button>
          </li>
          <li>
            <button class="sidebar-btn ${appState.activeTab === 'orders' ? 'active' : ''}" data-tab="orders">
              <span>🚚</span>
              <span>${t.menuOrders} (${appState.orders.length})</span>
            </button>
          </li>

          <li class="sidebar-item-header" style="margin-top: 14px;">Bán Lẻ & Hồ Sơ (FE-01 + FE-08)</li>
          <li>
            <button class="sidebar-btn ${appState.activeTab === 'catalogue' ? 'active' : ''}" data-tab="catalogue">
              <span>💍</span>
              <span>${t.menuCatalogue}</span>
            </button>
          </li>
          <li>
            <button class="sidebar-btn ${appState.activeTab === 'profile' ? 'active' : ''}" data-tab="profile">
              <span>👤</span>
              <span>${t.menuProfile}</span>
            </button>
          </li>
        </ul>

        <div class="sidebar-footer">
          <div>Logged in: <strong>${appState.profile.fullName}</strong></div>
          <div style="font-size: 11px; opacity: 0.7; margin-top: 2px;">Role: ${appState.role}</div>
        </div>
      </aside>

      <!-- Console Main Body -->
      <div class="console-main">
        <!-- Topbar -->
        <header class="console-topbar">
          <div class="topbar-left">
            <span style="font-size: 12px; color: var(--color-text-secondary); font-weight: 600;">${t.branchLabel}</span>
            <select id="branchSelect" class="branch-select-box">
              ${branches.map(b => `
                <option value="${b.id}" ${b.id === appState.selectedBranch ? 'selected' : ''}>
                  ${appState.lang === 'vi' ? b.nameVi : b.nameEn}
                </option>
              `).join('')}
            </select>
          </div>

          <div class="topbar-right">
            <!-- Role Toggle -->
            <div class="control-pill-group">
              <button class="control-pill-btn ${appState.role === 'STAFF' ? 'active' : ''}" id="btnRoleStaff">
                ${t.roleStaff}
              </button>
              <button class="control-pill-btn ${appState.role === 'CUSTOMER' ? 'active' : ''}" id="btnRoleCustomer">
                ${t.roleCustomer}
              </button>
            </div>

            <!-- Language VI/EN -->
            <div class="control-pill-group">
              <button class="control-pill-btn ${appState.lang === 'vi' ? 'active' : ''}" id="btnLangVi">VI</button>
              <button class="control-pill-btn ${appState.lang === 'en' ? 'active' : ''}" id="btnLangEn">EN</button>
            </div>

            <button class="cart-btn-indicator" id="openCartBtn">
              <span>🛒 Giỏ</span>
              <span class="cart-badge-count">${totalCartCount}</span>
            </button>
          </div>
        </header>

        <!-- Dynamic Content Body -->
        <div class="console-content" id="consoleContent"></div>
      </div>

      <!-- Cart Drawer -->
      <div class="drawer-backdrop" id="cartBackdrop"></div>
      <aside class="cart-drawer" id="cartDrawer">
        <div class="drawer-header">
          <div class="drawer-title">
            <span>🛒</span>
            <span>${t.cartTitle}</span>
          </div>
          <button class="close-btn" id="closeCartBtn">&times;</button>
        </div>
        <div class="drawer-body" id="cartDrawerBody"></div>
        <div class="drawer-footer" id="cartDrawerFooter"></div>
      </aside>

      <div class="toast-container" id="toastContainer"></div>
    `;

    bindEvents();
    renderContent();
    renderCart();
  }

  function bindEvents() {
    document.querySelectorAll(".sidebar-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        appState.activeTab = btn.getAttribute("data-tab");
        saveState();
        renderApp();
      });
    });

    document.getElementById("btnRoleCustomer")?.addEventListener("click", () => {
      appState.role = "CUSTOMER";
      if (appState.activeTab === 'staffQueue') appState.activeTab = 'catalogue';
      saveState();
      renderApp();
    });

    document.getElementById("btnRoleStaff")?.addEventListener("click", () => {
      appState.role = "STAFF";
      appState.activeTab = "staffQueue";
      saveState();
      renderApp();
    });

    document.getElementById("btnLangVi")?.addEventListener("click", () => {
      appState.lang = "vi";
      saveState();
      renderApp();
    });

    document.getElementById("btnLangEn")?.addEventListener("click", () => {
      appState.lang = "en";
      saveState();
      renderApp();
    });

    document.getElementById("branchSelect")?.addEventListener("change", (e) => {
      appState.selectedBranch = e.target.value;
      saveState();
      renderApp();
      showToast(appState.lang === 'vi' ? 'Đã đổi chi nhánh làm việc' : 'Branch switched');
    });

    document.getElementById("openCartBtn")?.addEventListener("click", openCart);
    document.getElementById("closeCartBtn")?.addEventListener("click", closeCart);
    document.getElementById("cartBackdrop")?.addEventListener("click", closeCart);
  }

  function openCart() {
    document.getElementById("cartBackdrop")?.classList.add("open");
    document.getElementById("cartDrawer")?.classList.add("open");
  }

  function closeCart() {
    document.getElementById("cartBackdrop")?.classList.remove("open");
    document.getElementById("cartDrawer")?.classList.remove("open");
  }

  function renderContent() {
    const container = document.getElementById("consoleContent");
    if (!container) return;

    switch (appState.activeTab) {
      case 'staffQueue':
        renderStaffTable(container);
        break;
      case 'orders':
        renderOrdersTable(container);
        break;
      case 'catalogue':
        renderCatalogueGrid(container);
        break;
      case 'profile':
        renderProfileConsole(container);
        break;
      default:
        renderStaffTable(container);
    }
  }

  // --- STAFF PACKING STATION TABLE ---
  function renderStaffTable(container) {
    const t = i18n[appState.lang];
    const branchOrders = appState.orders.filter(o => o.branchId === appState.selectedBranch);
    const branchInfo = branches.find(b => b.id === appState.selectedBranch);

    container.innerHTML = `
      <div class="console-header-block">
        <div>
          <h2 class="console-title">
            <span>📦</span>
            <span>Bàn Đóng Gói Chi Nhánh: ${appState.lang === 'vi' ? branchInfo.nameVi : branchInfo.nameEn}</span>
          </h2>
          <p class="console-subtitle">
            Hàng đợi xử lý đơn hàng, dán nhãn bưu kiện và bàn giao bưu cục GHTK cho chi nhánh hiện tại.
          </p>
        </div>
        <div>
          <span class="badge-status badge-shipping" style="font-size: 12px; padding: 4px 10px;">
            ${branchOrders.length} Đơn hàng đang quản lý
          </span>
        </div>
      </div>

      <div class="data-table-container">
        <div class="data-table-toolbar">
          <input type="text" class="search-input" placeholder="🔍 Tìm theo mã đơn, khách hàng, mã GHTK...">
          <div style="font-size: 12px; color: var(--color-text-secondary);">
            Phạm vi chi nhánh: <strong>${branchInfo.id}</strong>
          </div>
        </div>

        <table class="console-table">
          <thead>
            <tr>
              <th>Mã Đơn Hàng</th>
              <th>Thời Gian</th>
              <th>Người Nhận (FE-01)</th>
              <th>Mặt Hàng (FE-08)</th>
              <th>Mã Vận Đơn GHTK</th>
              <th>Trạng Thái</th>
              <th>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            ${branchOrders.length === 0 ? `
              <tr>
                <td colspan="7" style="text-align: center; padding: 30px; color: var(--color-text-secondary);">
                  Không có đơn hàng nào tại chi nhánh này trong phiên làm việc.
                </td>
              </tr>
            ` : branchOrders.map(order => `
              <tr>
                <td><span class="code-mono">${order.orderId}</span></td>
                <td><span style="color: var(--color-text-secondary);">${order.createdAt}</span></td>
                <td>
                  <strong>${order.recipient.name}</strong>
                  <div style="font-size: 11px; color: var(--color-text-secondary);">${order.recipient.phone}</div>
                </td>
                <td>
                  ${order.items.map(it => `
                    <div>• ${it.nameVi} (Sz ${it.size}) × ${it.quantity}</div>
                  `).join('')}
                </td>
                <td>
                  <span class="code-mono" style="color: #0284C7; background: #E0F2FE; padding: 2px 6px; border-radius: 4px;">
                    ${order.carrierCode}
                  </span>
                </td>
                <td>
                  <span class="badge-status ${getStatusBadgeClass(order.status)}">
                    ${getStatusText(order.status)}
                  </span>
                </td>
                <td>
                  ${order.status === 'PAYMENT_CONFIRMED' ? `
                    <button class="btn-secondary" style="font-size: 11.5px; color: #D97706;" onclick="window.staffUpdate('${order.orderId}', 'PACKING')">
                      📦 ${t.staffStartPacking}
                    </button>
                  ` : order.status === 'PACKING' ? `
                    <button class="btn-primary" style="font-size: 11.5px;" onclick="window.staffUpdate('${order.orderId}', 'DISPATCHED_GHTK')">
                      🚚 ${t.staffDispatch}
                    </button>
                  ` : `
                    <span style="font-size: 12px; color: var(--color-success); font-weight: 600;">✓ Hoàn tất</span>
                  `}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  window.staffUpdate = function (orderId, newStatus) {
    const order = appState.orders.find(o => o.orderId === orderId);
    if (!order) return;
    order.status = newStatus;
    saveState();
    renderApp();
    showToast(`Đơn ${orderId}: Đã chuyển sang ${getStatusText(newStatus)}`);
  };

  // --- ORDERS TABLE ---
  function renderOrdersTable(container) {
    const t = i18n[appState.lang];

    container.innerHTML = `
      <div class="console-header-block">
        <div>
          <h2 class="console-title">
            <span>🚚</span>
            <span>${t.menuOrders}</span>
          </h2>
          <p class="console-subtitle">
            Danh sách toàn bộ các đơn hàng chi nhánh đã xác thực thanh toán và tạo kiện GHTK.
          </p>
        </div>
      </div>

      <div class="data-table-container">
        <table class="console-table">
          <thead>
            <tr>
              <th>Mã Đơn</th>
              <th>Chi Nhánh</th>
              <th>Ngày Đặt</th>
              <th>Sản Phẩm</th>
              <th>Tiền Hàng</th>
              <th>Phí GHTK</th>
              <th>Tổng Thu</th>
              <th>Mã GHTK</th>
              <th>Trạng Thái</th>
            </tr>
          </thead>
          <tbody>
            ${appState.orders.length === 0 ? `
              <tr><td colspan="9" style="text-align: center; padding: 24px;">Chưa có đơn hàng nào.</td></tr>
            ` : appState.orders.map(order => {
              const b = branches.find(br => br.id === order.branchId);
              return `
                <tr>
                  <td><span class="code-mono">${order.orderId}</span></td>
                  <td>${appState.lang === 'vi' ? b.nameVi.split(' ')[0] : 'Branch'}</td>
                  <td style="color: var(--color-text-secondary);">${order.createdAt}</td>
                  <td>
                    ${order.items.map(it => `<div>${it.nameVi} (Sz ${it.size}) × ${it.quantity}</div>`).join('')}
                  </td>
                  <td>${formatCurrency(order.subtotal)}</td>
                  <td>${formatCurrency(order.shippingFee)}</td>
                  <td><strong>${formatCurrency(order.total)}</strong></td>
                  <td><span class="code-mono">${order.carrierCode}</span></td>
                  <td>
                    <span class="badge-status ${getStatusBadgeClass(order.status)}">
                      ${getStatusText(order.status)}
                    </span>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  // --- CATALOGUE DATA-GRID ---
  function renderCatalogueGrid(container) {
    const t = i18n[appState.lang];
    const currentBranch = branches.find(b => b.id === appState.selectedBranch);

    container.innerHTML = `
      <div class="console-header-block">
        <div>
          <h2 class="console-title">
            <span>💍</span>
            <span>Kho Hàng Bán Lẻ: ${appState.lang === 'vi' ? currentBranch.nameVi : currentBranch.nameEn}</span>
          </h2>
          <p class="console-subtitle">
            Danh mục sản phẩm nhẫn bán sẵn (FE-02 handoff sang FE-08 checkout).
          </p>
        </div>
      </div>

      <div class="data-table-container">
        <table class="console-table">
          <thead>
            <tr>
              <th>Mã SKU</th>
              <th>Tên Sản Phẩm</th>
              <th>Đơn Giá</th>
              <th>Trọng Lượng / Quy Cách</th>
              <th>Tồn Kho Chi Nhánh</th>
              <th>Chọn Size</th>
              <th>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            ${productsCatalogue.map(product => {
              const stock = product.stock[appState.selectedBranch] || 0;
              const isAvailable = stock > 0;
              const curSize = appState.selectedSizes[product.id] || product.sizes[0];

              return `
                <tr>
                  <td><span class="code-mono">${product.sku}</span></td>
                  <td><strong>${appState.lang === 'vi' ? product.nameVi : product.nameEn}</strong></td>
                  <td><span style="font-weight: 600; color: #0F172A;">${formatCurrency(product.price)}</span></td>
                  <td style="color: var(--color-text-secondary); font-size: 12px;">${product.weightGrams}g &bull; ${product.dimensions}</td>
                  <td>
                    <span class="badge-status ${isAvailable ? 'badge-paid' : 'badge-packing'}">
                      ${isAvailable ? `Còn ${stock}` : 'Hết hàng'}
                    </span>
                  </td>
                  <td>
                    <select onchange="window.selectSize('${product.id}', parseInt(this.value))" style="padding: 2px 6px; border-radius: 4px; border: 1px solid var(--color-border); font-size: 12px;">
                      ${product.sizes.map(s => `
                        <option value="${s}" ${s === curSize ? 'selected' : ''}>Size ${s}</option>
                      `).join('')}
                    </select>
                  </td>
                  <td>
                    <button class="btn-primary" ${!isAvailable ? 'disabled' : ''} onclick="window.addToCart('${product.id}')">
                      + ${t.addToCart}
                    </button>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  window.selectSize = function (productId, size) {
    appState.selectedSizes[productId] = size;
    saveState();
  };

  window.addToCart = function (productId) {
    const product = productsCatalogue.find(p => p.id === productId);
    if (!product) return;

    const size = appState.selectedSizes[productId] || product.sizes[0];
    const branchId = appState.selectedBranch;

    const existing = appState.cart.find(
      item => item.productId === productId && item.branchId === branchId && item.size === size
    );

    if (existing) {
      existing.quantity += 1;
    } else {
      appState.cart.push({
        productId,
        branchId,
        size,
        quantity: 1,
        price: product.price,
        sku: product.sku,
        nameVi: product.nameVi,
        nameEn: product.nameEn
      });
    }

    saveState();
    renderApp();
    showToast(`Đã thêm vào giỏ: ${product.nameVi}`);
    openCart();
  };

  function renderCart() {
    const t = i18n[appState.lang];
    const body = document.getElementById("cartDrawerBody");
    const footer = document.getElementById("cartDrawerFooter");
    if (!body || !footer) return;

    if (appState.cart.length === 0) {
      body.innerHTML = `
        <div style="text-align: center; padding: 40px 10px; color: var(--color-text-secondary);">
          <p>${t.emptyCart}</p>
        </div>
      `;
      footer.innerHTML = "";
      return;
    }

    const groupedByBranch = {};
    appState.cart.forEach((item, index) => {
      if (!groupedByBranch[item.branchId]) {
        groupedByBranch[item.branchId] = [];
      }
      groupedByBranch[item.branchId].push({ ...item, originalIndex: index });
    });

    let overallItemsSubtotal = 0;
    const branchOrderKeys = Object.keys(groupedByBranch);
    const ghtkFeePerBranch = 32000;
    const totalShipping = branchOrderKeys.length * ghtkFeePerBranch;

    let bodyHtml = `
      <div style="font-size: 11.5px; background: #FEF3C7; border: 1px solid #FCD34D; padding: 8px 10px; border-radius: 4px; color: #92400E; margin-bottom: 12px;">
        ℹ️ ${t.splitNotice}
      </div>
    `;

    branchOrderKeys.forEach(branchId => {
      const branchInfo = branches.find(b => b.id === branchId);
      const items = groupedByBranch[branchId];
      const branchSubtotal = items.reduce((sum, item) => sum + (item.price * item.quantity), 0);
      overallItemsSubtotal += branchSubtotal;

      bodyHtml += `
        <div class="branch-order-card">
          <div class="branch-order-title">
            <span>📍 ${appState.lang === 'vi' ? branchInfo.nameVi : branchInfo.nameEn}</span>
            <span style="color: var(--color-text-secondary); font-size: 11px;">${items.length} sp</span>
          </div>

          ${items.map(item => `
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 12.5px;">
              <div>
                <strong>${appState.lang === 'vi' ? item.nameVi : item.nameEn}</strong>
                <div style="font-size: 11px; color: var(--color-text-secondary);">Size ${item.size} &bull; SL: ${item.quantity}</div>
              </div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span>${formatCurrency(item.price * item.quantity)}</span>
                <button onclick="window.removeCartItem(${item.originalIndex})" style="background:none; border:none; color: #DC2626; cursor:pointer;">✕</button>
              </div>
            </div>
          `).join('')}

          <div style="border-top: 1px dashed var(--color-border); padding-top: 6px; font-size: 11.5px; display: flex; justify-content: space-between; color: var(--color-text-secondary);">
            <span>${t.shippingGhtk}</span>
            <span class="code-mono">${formatCurrency(ghtkFeePerBranch)}</span>
          </div>
        </div>
      `;
    });

    body.innerHTML = bodyHtml;

    const combinedTotal = overallItemsSubtotal + totalShipping;
    footer.innerHTML = `
      <div style="margin-bottom: 12px; font-size: 12px;">
        <div style="color: var(--color-text-secondary);">${t.customerInfoTitle}</div>
        <div style="font-weight: 600;">${appState.profile.fullName} &bull; ${appState.profile.phone}</div>
        <div style="font-size: 11px; color: var(--color-text-secondary);">${appState.profile.address}</div>
      </div>

      <div style="display: flex; justify-content: space-between; font-size: 12.5px; margin-bottom: 4px;">
        <span>${t.branchSubtotal}</span>
        <span>${formatCurrency(overallItemsSubtotal)}</span>
      </div>
      <div style="display: flex; justify-content: space-between; font-size: 12.5px; margin-bottom: 6px;">
        <span>Phí ship GHTK (${branchOrderKeys.length} kiện):</span>
        <span>${formatCurrency(totalShipping)}</span>
      </div>
      <div style="display: flex; justify-content: space-between; font-weight: 700; font-size: 14px; border-top: 1px solid var(--color-border); padding-top: 8px; margin-top: 6px;">
        <span>${t.totalPayable}</span>
        <span style="color: #0F172A;">${formatCurrency(combinedTotal)}</span>
      </div>
      <button class="btn-primary" style="width: 100%; justify-content: center; margin-top: 12px; padding: 10px;" onclick="window.executeCheckout()">
        💳 ${t.checkoutBtn}
      </button>
    `;
  }

  window.removeCartItem = function (index) {
    appState.cart.splice(index, 1);
    saveState();
    renderApp();
    openCart();
  };

  window.executeCheckout = function () {
    const t = i18n[appState.lang];
    if (appState.cart.length === 0) return;

    const grouped = {};
    appState.cart.forEach(item => {
      if (!grouped[item.branchId]) grouped[item.branchId] = [];
      grouped[item.branchId].push(item);
    });

    const now = new Date();
    const timeStr = now.toISOString().slice(0, 16).replace('T', ' ');

    Object.keys(grouped).forEach((branchId) => {
      const items = grouped[branchId];
      const subtotal = items.reduce((s, it) => s + it.price * it.quantity, 0);
      const shippingFee = 32000;
      const orderNum = Math.floor(1000 + Math.random() * 9000);
      const branchCode = branchId === 'BRANCH_HN' ? 'HN' : 'SG';

      appState.orders.unshift({
        orderId: `ML-${branchCode}-${orderNum}`,
        branchId: branchId,
        createdAt: timeStr,
        items: items,
        subtotal: subtotal,
        shippingFee: shippingFee,
        total: subtotal + shippingFee,
        status: "PAYMENT_CONFIRMED",
        carrierCode: `GHTK-${branchCode}-${now.getFullYear()}${now.getMonth() + 1}${now.getDate()}-${orderNum}`,
        fulfilment: "DELIVERY_GHTK",
        recipient: {
          name: appState.profile.fullName,
          phone: appState.profile.phone,
          address: appState.profile.address
        }
      });
    });

    appState.cart = [];
    appState.activeTab = "orders";
    saveState();
    closeCart();
    renderApp();
    showToast(t.paymentSuccess);
  };

  // --- MEMBER PROFILE CONSOLE (FE-01) ---
  function renderProfileConsole(container) {
    const t = i18n[appState.lang];

    container.innerHTML = `
      <div class="console-header-block">
        <div>
          <h2 class="console-title">
            <span>👤</span>
            <span>${t.menuProfile}</span>
          </h2>
          <p class="console-subtitle">Quản lý định danh thành viên và địa chỉ giao hàng mặc định.</p>
        </div>
        <span class="badge-status badge-paid">
          ✓ ${t.verified}
        </span>
      </div>

      <div style="background: #FFFFFF; border: 1px solid var(--color-border); border-radius: var(--rounded-lg); padding: 20px; max-width: 600px;">
        <form onsubmit="window.saveProfileForm(event)">
          <div style="margin-bottom: 14px;">
            <label style="display: block; font-size: 12.5px; font-weight: 600; margin-bottom: 4px;">${t.fullName}</label>
            <input type="text" id="profFullName" value="${appState.profile.fullName}" required style="width: 100%; padding: 8px 10px; border: 1px solid var(--color-border); border-radius: 4px;">
          </div>

          <div style="margin-bottom: 14px;">
            <label style="display: block; font-size: 12.5px; font-weight: 600; margin-bottom: 4px;">${t.email}</label>
            <input type="email" value="${appState.profile.email}" disabled style="width: 100%; padding: 8px 10px; border: 1px solid var(--color-border); border-radius: 4px; background: #F8FAFC;">
          </div>

          <div style="margin-bottom: 14px;">
            <label style="display: block; font-size: 12.5px; font-weight: 600; margin-bottom: 4px;">${t.phone}</label>
            <input type="tel" id="profPhone" value="${appState.profile.phone}" required style="width: 100%; padding: 8px 10px; border: 1px solid var(--color-border); border-radius: 4px;">
          </div>

          <div style="margin-bottom: 18px;">
            <label style="display: block; font-size: 12.5px; font-weight: 600; margin-bottom: 4px;">${t.address}</label>
            <textarea id="profAddress" rows="3" required style="width: 100%; padding: 8px 10px; border: 1px solid var(--color-border); border-radius: 4px;">${appState.profile.address}</textarea>
          </div>

          <button type="submit" class="btn-primary">
            💾 ${t.saveProfile}
          </button>
        </form>
      </div>
    `;
  }

  window.saveProfileForm = function (e) {
    e.preventDefault();
    const t = i18n[appState.lang];
    appState.profile.fullName = document.getElementById("profFullName").value.trim();
    appState.profile.phone = document.getElementById("profPhone").value.trim();
    appState.profile.address = document.getElementById("profAddress").value.trim();
    saveState();
    renderApp();
    showToast(t.profileSaved);
  };

  function getStatusBadgeClass(status) {
    switch (status) {
      case 'PAYMENT_CONFIRMED': return 'badge-paid';
      case 'PACKING': return 'badge-packing';
      case 'DISPATCHED_GHTK':
      case 'IN_TRANSIT': return 'badge-shipping';
      case 'DELIVERED': return 'badge-delivered';
      default: return 'badge-paid';
    }
  }

  function getStatusText(status) {
    const t = i18n[appState.lang];
    switch (status) {
      case 'PAYMENT_CONFIRMED': return t.orderPaid;
      case 'PACKING': return t.orderPacking;
      case 'DISPATCHED_GHTK': return t.orderDispatched;
      case 'IN_TRANSIT': return t.orderInTransit;
      case 'DELIVERED': return t.orderDelivered;
      default: return status;
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    renderApp();
  });

})();
