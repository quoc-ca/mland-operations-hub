/**
 * PPSWBS Prototype - Style 1: Atelier Noir (Craft Dark)
 * Features 001 (Account Management & Profile) & 008 (Retail Order & Fulfillment)
 * Implementation adhering strictly to ppswbs-spec/DESIGN.md & ppswbs_frontend/AGENT.md
 */

(function () {
  'use strict';

  // --- LOCALIZATION DICTIONARY (VI / EN) ---
  const i18n = {
    vi: {
      brandTag: "Craft Dark",
      roleCustomer: "Khách hàng",
      roleStaff: "Nhân viên (Staff)",
      branchLabel: "Chi nhánh duyệt mua:",
      tabCatalogue: "Sản Phẩm Sẵn Có",
      tabOrders: "Theo Dõi Đơn Hàng",
      tabProfile: "Hồ Sơ Thành Viên",
      tabStaffQueue: "Hàng Đợi Đóng Gói (Staff)",
      cartTitle: "Giỏ hàng của bạn",
      emptyCart: "Giỏ hàng trống. Hãy chọn sản phẩm từ xưởng chế tác.",
      addToCart: "Thêm Vào Giỏ",
      outOfStock: "Hết Hàng Tại Chi Nhánh",
      stockAvailable: "Còn hàng",
      ringSize: "Kích cỡ nhẫn (Size):",
      splitNotice: "Đơn hàng từ các chi nhánh khác nhau sẽ được tách thành các kiện hàng riêng biệt.",
      branchSubtotal: "Tiền hàng:",
      shippingGhtk: "Phí vận chuyển GHTK:",
      deliveryOption: "Hình thức nhận:",
      pickupStore: "Nhận tại cửa hàng (Miễn phí)",
      deliverGhtk: "Giao hàng GHTK",
      totalPayable: "Tổng thanh toán:",
      checkoutBtn: "Tiến Hành Thanh Toán VNPay",
      customerInfoTitle: "Thông tin người nhận (Từ Hồ sơ FE-01)",
      fullName: "Họ và tên:",
      phone: "Số điện thoại:",
      email: "Email thành viên:",
      verified: "Đã xác minh",
      address: "Địa chỉ giao hàng:",
      saveProfile: "Lưu Thay Đổi Hồ Sơ",
      profileSaved: "Đã cập nhật thông tin hồ sơ thành công!",
      paymentSuccess: "Thanh toán thành công! Mã đơn hàng đã được khởi tạo.",
      orderPlaced: "Đã đặt hàng",
      orderPaid: "Đã thanh toán VNPay",
      orderPacking: "Đang đóng gói",
      orderDispatched: "Đã bàn giao GHTK",
      orderInTransit: "Đang vận chuyển",
      orderDelivered: "Giao thành công",
      carrierCode: "Mã vận đơn GHTK:",
      staffStartPacking: "Bắt đầu đóng gói",
      staffDispatch: "Bàn giao cho shipper GHTK",
      filterAll: "Tất cả trạng thái",
      itemsOrdered: "Sản phẩm",
      branchOrderHeader: "Đơn hàng xử lý tại:",
      backToHub: "← Trở về Hub",
    },
    en: {
      brandTag: "Craft Dark",
      roleCustomer: "Customer",
      roleStaff: "Staff Member",
      branchLabel: "Active Branch Context:",
      tabCatalogue: "Ready-to-Sell Rings",
      tabOrders: "Order Tracking",
      tabProfile: "Member Profile",
      tabStaffQueue: "Fulfillment Queue (Staff)",
      cartTitle: "Your Shopping Bag",
      emptyCart: "Your bag is empty. Explore artisan handcrafted rings.",
      addToCart: "Add to Bag",
      outOfStock: "Out of Stock at this Branch",
      stockAvailable: "In Stock",
      ringSize: "Ring Size:",
      splitNotice: "Items from different branches will be fulfilled as separate branch orders.",
      branchSubtotal: "Items Subtotal:",
      shippingGhtk: "GHTK Delivery Fee:",
      deliveryOption: "Fulfilment Method:",
      pickupStore: "In-store Pickup (Free)",
      deliverGhtk: "GHTK Standard Delivery",
      totalPayable: "Combined Total:",
      checkoutBtn: "Proceed to VNPay Checkout",
      customerInfoTitle: "Recipient Details (From Profile FE-01)",
      fullName: "Full Name:",
      phone: "Phone Number:",
      email: "Member Email:",
      verified: "Verified",
      address: "Delivery Address:",
      saveProfile: "Save Profile Changes",
      profileSaved: "Profile information updated successfully!",
      paymentSuccess: "Payment confirmed! Branch orders successfully created.",
      orderPlaced: "Order Placed",
      orderPaid: "VNPay Paid",
      orderPacking: "Packing in Progress",
      orderDispatched: "Dispatched to GHTK",
      orderInTransit: "In Transit",
      orderDelivered: "Delivered",
      carrierCode: "GHTK Tracking Code:",
      staffStartPacking: "Start Packing",
      staffDispatch: "Dispatch to GHTK Carrier",
      filterAll: "All Statuses",
      itemsOrdered: "Items",
      branchOrderHeader: "Processing Branch:",
      backToHub: "← Back to Hub",
    }
  };

  // --- INITIAL DATA & SEED STATE ---
  const branches = [
    { id: "BRANCH_HN", nameVi: "Hà Nội Atelier (42 Nhà Chung, Hoàn Kiếm)", nameEn: "Hanoi Atelier (42 Nha Chung)" },
    { id: "BRANCH_SG", nameVi: "Sài Gòn Craft Lab (150 Pasteur, Quận 1)", nameEn: "Saigon Craft Lab (150 Pasteur)" }
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
      },
      imageSvg: `<svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="50" cy="50" r="32" stroke="#EEDC35" stroke-width="8"/>
        <ellipse cx="50" cy="30" rx="10" ry="4" fill="#FFFFFF" fill-opacity="0.3"/>
      </svg>`
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
      },
      imageSvg: `<svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="50" cy="54" r="30" stroke="#EEDC35" stroke-width="7"/>
        <polygon points="50,14 62,26 38,26" fill="#38BDF8"/>
        <polygon points="50,34 62,26 38,26" fill="#E0F2FE"/>
      </svg>`
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
        BRANCH_SG: 0 // Out of stock at SG branch
      },
      imageSvg: `<svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="50" cy="50" r="34" stroke="#D1D5DB" stroke-width="9"/>
        <path d="M40 50 L60 50 M50 40 L50 60" stroke="#EEDC35" stroke-width="3"/>
      </svg>`
    }
  ];

  // Global State stored in localStorage with fallback
  const STATE_KEY = "MLAND_PROTOTYPE_STATE_V1";
  let appState = {
    lang: "vi",
    role: "CUSTOMER", // CUSTOMER | STAFF
    selectedBranch: "BRANCH_HN",
    activeTab: "catalogue", // catalogue | orders | profile | staffQueue
    selectedSizes: { P01: 11, P02: 12, P03: 14 },
    cart: [], // items with: { productId, branchId, size, quantity, price, sku, nameVi, nameEn }
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
        status: "DISPATCHED_GHTK", // PAYMENT_CONFIRMED | PACKING | DISPATCHED_GHTK | IN_TRANSIT | DELIVERED
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

  // Try to load persisted state
  try {
    const saved = localStorage.getItem(STATE_KEY);
    if (saved) {
      const parsed = JSON.parse(saved);
      appState = { ...appState, ...parsed };
    }
  } catch (e) {
    console.warn("Storage access failed, using fresh state", e);
  }

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
    toast.innerHTML = `<span>✨</span> <span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 200);
    }, 2800);
  }

  // --- RENDER CONTROLS & VIEWS ---
  function renderApp() {
    const t = i18n[appState.lang];
    const appRoot = document.getElementById("app");
    if (!appRoot) return;

    // Calculate total cart items
    const totalCartCount = appState.cart.reduce((sum, item) => sum + item.quantity, 0);

    appRoot.innerHTML = `
      <!-- Top Navbar -->
      <nav class="top-navbar">
        <div class="brand-section">
          <a href="../index.html" class="brand-logo" title="${t.backToHub}">
            MLAND <span>ATELIER</span>
            <span class="tag">${t.brandTag}</span>
          </a>
          <a href="../index.html" class="btn-secondary" style="font-size: 11px; padding: 4px 8px;">
            ${t.backToHub}
          </a>
        </div>

        <div class="nav-actions">
          <!-- Active Branch Selector (FE-08 Rule: Chosen before browsing) -->
          ${appState.role === 'CUSTOMER' ? `
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 12px; color: var(--color-text-secondary);">${t.branchLabel}</span>
              <select id="branchSelect" class="branch-select-box">
                ${branches.map(b => `
                  <option value="${b.id}" ${b.id === appState.selectedBranch ? 'selected' : ''}>
                    ${appState.lang === 'vi' ? b.nameVi : b.nameEn}
                  </option>
                `).join('')}
              </select>
            </div>
          ` : `
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 12px; color: var(--color-primary); font-weight: 600;">Staff Branch:</span>
              <select id="staffBranchSelect" class="branch-select-box">
                ${branches.map(b => `
                  <option value="${b.id}" ${b.id === appState.selectedBranch ? 'selected' : ''}>
                    ${appState.lang === 'vi' ? b.nameVi : b.nameEn}
                  </option>
                `).join('')}
              </select>
            </div>
          `}

          <!-- Role Switcher -->
          <div class="control-pill-group">
            <button class="control-pill-btn ${appState.role === 'CUSTOMER' ? 'active' : ''}" id="btnRoleCustomer">
              ${t.roleCustomer}
            </button>
            <button class="control-pill-btn ${appState.role === 'STAFF' ? 'active' : ''}" id="btnRoleStaff">
              ${t.roleStaff}
            </button>
          </div>

          <!-- Language Switcher VI / EN -->
          <div class="control-pill-group">
            <button class="control-pill-btn ${appState.lang === 'vi' ? 'active' : ''}" id="btnLangVi">VI</button>
            <button class="control-pill-btn ${appState.lang === 'en' ? 'active' : ''}" id="btnLangEn">EN</button>
          </div>

          <!-- Customer Cart Button -->
          ${appState.role === 'CUSTOMER' ? `
            <button class="cart-btn-indicator" id="openCartBtn">
              <span>🛒</span>
              <span class="cart-badge-count">${totalCartCount}</span>
            </button>
          ` : ''}
        </div>
      </nav>

      <!-- Main Content Container -->
      <main class="app-container">
        <!-- Hero Header -->
        <header class="view-hero">
          <div>
            <h1>${appState.role === 'CUSTOMER' ? 'Atelier Jewelry Experience' : 'Workshop Fulfillment Operations'}</h1>
            <p>${appState.role === 'CUSTOMER' 
              ? (appState.lang === 'vi' ? 'Chế tác trang sức thủ công & Giao nhận độc quyền Mland.' : 'Bespoke jewellery craftsmanship & multi-branch fulfillment.') 
              : (appState.lang === 'vi' ? 'Xử lý đơn hàng, đóng gói và đồng bộ trạng thái GHTK theo từng chi nhánh.' : 'Staff operations, order packaging & GHTK carrier synchronization.')}
            </p>
          </div>
          <div>
            <span style="font-family: var(--font-mono); font-size: 12px; color: var(--color-primary);">
              Member: ${appState.profile.fullName} (${appState.profile.email})
            </span>
          </div>
        </header>

        <!-- Navigation Tabs -->
        <div class="view-tabs">
          ${appState.role === 'CUSTOMER' ? `
            <button class="view-tab-btn ${appState.activeTab === 'catalogue' ? 'active' : ''}" data-tab="catalogue">
              ${t.tabCatalogue}
            </button>
            <button class="view-tab-btn ${appState.activeTab === 'orders' ? 'active' : ''}" data-tab="orders">
              ${t.tabOrders} (${appState.orders.length})
            </button>
            <button class="view-tab-btn ${appState.activeTab === 'profile' ? 'active' : ''}" data-tab="profile">
              ${t.tabProfile}
            </button>
          ` : `
            <button class="view-tab-btn ${appState.activeTab === 'staffQueue' ? 'active' : ''}" data-tab="staffQueue">
              ${t.tabStaffQueue}
            </button>
          `}
        </div>

        <!-- Dynamic Tab View Body -->
        <div id="tabContent"></div>
      </main>

      <!-- Cart Slide-out Drawer -->
      <div class="drawer-backdrop" id="cartBackdrop"></div>
      <aside class="cart-drawer" id="cartDrawer">
        <div class="drawer-header">
          <div class="drawer-title">
            <span>👜</span>
            <span>${t.cartTitle}</span>
          </div>
          <button class="close-btn" id="closeCartBtn">&times;</button>
        </div>
        <div class="drawer-body" id="cartDrawerBody"></div>
        <div class="drawer-footer" id="cartDrawerFooter"></div>
      </aside>

      <!-- Toast Alerts Container -->
      <div class="toast-container" id="toastContainer"></div>
    `;

    bindHeaderEvents();
    renderActiveTab();
    renderCart();
  }

  // --- HEADER EVENT BINDING ---
  function bindHeaderEvents() {
    const t = i18n[appState.lang];

    document.getElementById("btnRoleCustomer")?.addEventListener("click", () => {
      appState.role = "CUSTOMER";
      appState.activeTab = "catalogue";
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
      showToast(appState.lang === 'vi' ? 'Đã đổi chi nhánh duyệt mua' : 'Switched active browsing branch');
    });

    document.getElementById("staffBranchSelect")?.addEventListener("change", (e) => {
      appState.selectedBranch = e.target.value;
      saveState();
      renderApp();
    });

    // Tab buttons
    document.querySelectorAll(".view-tab-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        appState.activeTab = btn.getAttribute("data-tab");
        saveState();
        renderApp();
      });
    });

    // Cart open/close
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

  // --- TAB RENDERING ---
  function renderActiveTab() {
    const container = document.getElementById("tabContent");
    if (!container) return;

    if (appState.role === 'STAFF') {
      renderStaffQueue(container);
      return;
    }

    switch (appState.activeTab) {
      case 'catalogue':
        renderCatalogue(container);
        break;
      case 'orders':
        renderOrders(container);
        break;
      case 'profile':
        renderProfile(container);
        break;
      default:
        renderCatalogue(container);
    }
  }

  // --- CATALOGUE VIEW (FE-08 + FE-02 Hand-off) ---
  function renderCatalogue(container) {
    const t = i18n[appState.lang];
    const currentBranch = branches.find(b => b.id === appState.selectedBranch);

    container.innerHTML = `
      <div style="margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center;">
        <span style="font-size: 14px; color: var(--color-text-secondary);">
          Đang xem kho tại: <strong style="color: var(--color-primary);">${appState.lang === 'vi' ? currentBranch.nameVi : currentBranch.nameEn}</strong>
        </span>
        <span style="font-size: 12px; color: var(--color-text-secondary);">
          * Mỗi sản phẩm thêm vào giỏ sẽ ghi nhận chi nhánh tại thời điểm bấm.
        </span>
      </div>
      <div class="products-grid">
        ${productsCatalogue.map(product => {
          const stockAtBranch = product.stock[appState.selectedBranch] || 0;
          const isAvailable = stockAtBranch > 0;
          const selectedSize = appState.selectedSizes[product.id] || product.sizes[0];

          return `
            <div class="product-card" id="card-${product.id}">
              <div class="product-image-canvas">
                <span class="product-branch-tag">
                  ${appState.lang === 'vi' ? currentBranch.nameVi.split(' ')[0] : 'Branch'}
                </span>
                <span class="product-stock-tag ${isAvailable ? 'stock-in' : 'stock-out'}">
                  ${isAvailable ? `${t.stockAvailable} (${stockAtBranch})` : t.outOfStock}
                </span>
                ${product.imageSvg}
              </div>
              <div class="product-info">
                <div class="product-sku">SKU: ${product.sku} &bull; ${product.weightGrams}g</div>
                <h3 class="product-title">${appState.lang === 'vi' ? product.nameVi : product.nameEn}</h3>
                <div class="product-price">${formatCurrency(product.price)}</div>

                <!-- Variant Ring Size Selection -->
                <div class="product-variants-row">
                  <span class="variant-label">${t.ringSize}</span>
                  <div class="size-selector">
                    ${product.sizes.map(size => `
                      <button class="size-btn ${size === selectedSize ? 'active' : ''}" 
                              onclick="window.selectSize('${product.id}', ${size})">
                        ${size}
                      </button>
                    `).join('')}
                  </div>
                </div>

                <!-- Add to Cart Button (FE-08 Rule) -->
                <button class="btn-primary" 
                        ${!isAvailable ? 'disabled' : ''} 
                        onclick="window.addToCart('${product.id}')">
                  <span>🛍️</span>
                  <span>${isAvailable ? t.addToCart : t.outOfStock}</span>
                </button>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    `;
  }

  // Window helper for size selection
  window.selectSize = function (productId, size) {
    appState.selectedSizes[productId] = size;
    saveState();
    renderActiveTab();
  };

  // Window helper for adding product to cart (FE-08 Rule: captures currently selected branch)
  window.addToCart = function (productId) {
    const product = productsCatalogue.find(p => p.id === productId);
    if (!product) return;

    const size = appState.selectedSizes[productId] || product.sizes[0];
    const branchId = appState.selectedBranch;

    // Check existing item with same product, branch, and size
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
    showToast(appState.lang === 'vi' ? `Đã thêm vào giỏ: ${product.nameVi}` : `Added to bag: ${product.nameEn}`);
    openCart();
  };

  // --- CART RENDERING & MULTI-BRANCH CHECKOUT ---
  function renderCart() {
    const t = i18n[appState.lang];
    const body = document.getElementById("cartDrawerBody");
    const footer = document.getElementById("cartDrawerFooter");
    if (!body || !footer) return;

    if (appState.cart.length === 0) {
      body.innerHTML = `
        <div style="text-align: center; padding: 40px 10px; color: var(--color-text-secondary);">
          <div style="font-size: 40px; margin-bottom: 12px;">🛍️</div>
          <p>${t.emptyCart}</p>
        </div>
      `;
      footer.innerHTML = "";
      return;
    }

    // Group cart lines by Branch (FE-08 Rule: marketplace-style separate branch orders)
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
      <div style="font-size: 12px; background: rgba(238, 220, 53, 0.1); border: 1px solid rgba(238, 220, 53, 0.3); padding: 10px 12px; border-radius: 6px; color: var(--color-primary); margin-bottom: 14px;">
        ⚠️ ${t.splitNotice}
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
            <span style="font-size: 11px; color: var(--color-text-secondary);">${items.length} sp</span>
          </div>

          ${items.map(item => `
            <div class="cart-item-row">
              <div class="cart-item-info">
                <span class="cart-item-name">${appState.lang === 'vi' ? item.nameVi : item.nameEn}</span>
                <span class="cart-item-meta">Size: ${item.size} &bull; SL: ${item.quantity}</span>
              </div>
              <div class="cart-item-action">
                <span style="color: var(--color-primary); font-weight: 600;">${formatCurrency(item.price * item.quantity)}</span>
                <button class="remove-btn" onclick="window.removeCartItem(${item.originalIndex})">✕</button>
              </div>
            </div>
          `).join('')}

          <div style="border-top: 1px dashed var(--color-border); padding-top: 8px; margin-top: 8px; font-size: 12px; color: var(--color-text-secondary); display: flex; justify-content: space-between;">
            <span>${t.shippingGhtk}</span>
            <span style="color: var(--color-text-primary); font-family: var(--font-mono);">${formatCurrency(ghtkFeePerBranch)}</span>
          </div>
        </div>
      `;
    });

    body.innerHTML = bodyHtml;

    // Checkout Summary & Single Payment Action (FE-08 Rule)
    const combinedTotal = overallItemsSubtotal + totalShipping;
    footer.innerHTML = `
      <div style="margin-bottom: 12px; border-bottom: 1px solid var(--color-border); padding-bottom: 10px;">
        <div style="font-size: 12px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 4px;">
          ${t.customerInfoTitle}
        </div>
        <div style="font-size: 13px; color: var(--color-text-primary);">
          ${appState.profile.fullName} &bull; ${appState.profile.phone}
        </div>
        <div style="font-size: 12px; color: var(--color-text-secondary); text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">
          📍 ${appState.profile.address}
        </div>
      </div>

      <div class="summary-row">
        <span>${t.branchSubtotal}</span>
        <span>${formatCurrency(overallItemsSubtotal)}</span>
      </div>
      <div class="summary-row">
        <span>Phí ship GHTK (${branchOrderKeys.length} kiện):</span>
        <span>${formatCurrency(totalShipping)}</span>
      </div>
      <div class="summary-row total-row">
        <span>${t.totalPayable}</span>
        <span class="total-price">${formatCurrency(combinedTotal)}</span>
      </div>
      <button class="btn-primary" style="margin-top: 14px;" onclick="window.executeCheckout()">
        <span>💳</span>
        <span>${t.checkoutBtn}</span>
      </button>
    `;
  }

  window.removeCartItem = function (index) {
    appState.cart.splice(index, 1);
    saveState();
    renderApp();
    openCart();
  };

  // Execute Checkout (FE-08: Creates separate branch orders after verified payment)
  window.executeCheckout = function () {
    const t = i18n[appState.lang];
    if (appState.cart.length === 0) return;

    // Group items into Branch Orders
    const grouped = {};
    appState.cart.forEach(item => {
      if (!grouped[item.branchId]) grouped[item.branchId] = [];
      grouped[item.branchId].push(item);
    });

    const now = new Date();
    const timeStr = now.toISOString().slice(0, 16).replace('T', ' ');

    Object.keys(grouped).forEach((branchId, i) => {
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

    // Clear cart after accepted purchase
    appState.cart = [];
    appState.activeTab = "orders";
    saveState();
    closeCart();
    renderApp();
    showToast(t.paymentSuccess);
  };

  // --- ORDERS TRACKING VIEW (FE-08) ---
  function renderOrders(container) {
    const t = i18n[appState.lang];

    if (appState.orders.length === 0) {
      container.innerHTML = `
        <div style="text-align: center; padding: 40px; color: var(--color-text-secondary);">
          <p>Chưa có đơn hàng nào được tạo.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div style="display: flex; flex-direction: column; gap: 20px;">
        ${appState.orders.map(order => {
          const branch = branches.find(b => b.id === order.branchId);
          return `
            <div class="orders-table-wrapper" style="padding: 20px;">
              <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;">
                <div>
                  <span class="order-code" style="font-size: 16px;">${order.orderId}</span>
                  <span style="font-size: 12px; color: var(--color-text-secondary); margin-left: 10px;">
                    📅 ${order.createdAt}
                  </span>
                  <div style="font-size: 12px; color: var(--color-text-secondary); margin-top: 4px;">
                    ${t.branchOrderHeader} <strong style="color: var(--color-primary);">${appState.lang === 'vi' ? branch.nameVi : branch.nameEn}</strong>
                  </div>
                </div>

                <div>
                  <span class="carrier-tag">${order.carrierCode}</span>
                  <span class="badge-status ${getStatusBadgeClass(order.status)}" style="margin-left: 8px;">
                    ${getStatusText(order.status)}
                  </span>
                </div>
              </div>

              <!-- Tracking Stepper -->
              <div class="tracking-stepper">
                <div class="step-item ${isStepActive(order.status, 1) ? 'active' : ''}">
                  <div class="step-dot"></div>
                  <span>${t.orderPaid}</span>
                </div>
                <div class="step-item ${isStepActive(order.status, 2) ? 'active' : ''}">
                  <div class="step-dot"></div>
                  <span>${t.orderPacking}</span>
                </div>
                <div class="step-item ${isStepActive(order.status, 3) ? 'active' : ''}">
                  <div class="step-dot"></div>
                  <span>${t.orderDispatched}</span>
                </div>
                <div class="step-item ${isStepActive(order.status, 4) ? 'active' : ''}">
                  <div class="step-dot"></div>
                  <span>${t.orderInTransit}</span>
                </div>
                <div class="step-item ${isStepActive(order.status, 5) ? 'active' : ''}">
                  <div class="step-dot"></div>
                  <span>${t.orderDelivered}</span>
                </div>
              </div>

              <!-- Item details -->
              <div style="border-top: 1px solid var(--color-border); padding-top: 12px; margin-top: 12px; font-size: 13px;">
                <div style="color: var(--color-text-secondary); margin-bottom: 6px;">${t.itemsOrdered}:</div>
                ${order.items.map(it => `
                  <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                    <span>• ${appState.lang === 'vi' ? it.nameVi : it.nameEn} (Size ${it.size}) × ${it.quantity}</span>
                    <span style="font-family: var(--font-mono);">${formatCurrency(it.price * it.quantity)}</span>
                  </div>
                `).join('')}
                <div style="display: flex; justify-content: space-between; color: var(--color-text-secondary); margin-top: 6px;">
                  <span>${t.shippingGhtk}</span>
                  <span>${formatCurrency(order.shippingFee)}</span>
                </div>
                <div style="display: flex; justify-content: space-between; font-weight: 700; color: var(--color-primary); border-top: 1px dashed var(--color-border); padding-top: 6px; margin-top: 6px;">
                  <span>${t.totalPayable}</span>
                  <span>${formatCurrency(order.total)}</span>
                </div>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    `;
  }

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

  function isStepActive(status, stepNumber) {
    const weights = {
      'PAYMENT_CONFIRMED': 1,
      'PACKING': 2,
      'DISPATCHED_GHTK': 3,
      'IN_TRANSIT': 4,
      'DELIVERED': 5
    };
    return (weights[status] || 1) >= stepNumber;
  }

  // --- STAFF FULFILLMENT QUEUE VIEW (FE-08) ---
  function renderStaffQueue(container) {
    const t = i18n[appState.lang];
    const staffBranchOrders = appState.orders.filter(o => o.branchId === appState.selectedBranch);

    container.innerHTML = `
      <div style="margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;">
        <div>
          <span style="font-size: 15px; font-weight: 700; color: var(--color-primary);">
            Màn hình vận hành & Đóng gói tại: ${appState.selectedBranch === 'BRANCH_HN' ? 'Hà Nội Atelier' : 'Sài Gòn Craft Lab'}
          </span>
          <p style="font-size: 13px; color: var(--color-text-secondary); margin-top: 2px;">
            Nhân viên chỉ có quyền xử lý các đơn hàng thuộc chi nhánh phụ trách.
          </p>
        </div>
        <span class="badge-status badge-shipping">
          ${staffBranchOrders.length} đơn hàng cần xử lý
        </span>
      </div>

      <div class="orders-table-wrapper">
        <table class="orders-table">
          <thead>
            <tr>
              <th>Mã Đơn / Thời Gian</th>
              <th>Khách Hàng (FE-01)</th>
              <th>Chi Tiết Hàng</th>
              <th>Mã Vận Đơn GHTK</th>
              <th>Trạng Thái</th>
              <th>Thao Tác Vận Hành</th>
            </tr>
          </thead>
          <tbody>
            ${staffBranchOrders.length === 0 ? `
              <tr>
                <td colspan="6" style="text-align: center; padding: 30px; color: var(--color-text-secondary);">
                  Không có đơn hàng nào tại chi nhánh này. Hãy chuyển góc nhìn Khách hàng và đặt thử đơn hàng.
                </td>
              </tr>
            ` : staffBranchOrders.map(order => `
              <tr>
                <td>
                  <div class="order-code">${order.orderId}</div>
                  <div style="font-size: 11px; color: var(--color-text-secondary);">${order.createdAt}</div>
                </td>
                <td>
                  <div style="font-weight: 600;">${order.recipient.name}</div>
                  <div style="font-size: 11px; color: var(--color-text-secondary);">${order.recipient.phone}</div>
                </td>
                <td>
                  ${order.items.map(it => `
                    <div style="font-size: 12px;">• ${it.nameVi} (Sz ${it.size}) × ${it.quantity}</div>
                  `).join('')}
                </td>
                <td>
                  <span class="carrier-tag">${order.carrierCode}</span>
                </td>
                <td>
                  <span class="badge-status ${getStatusBadgeClass(order.status)}">
                    ${getStatusText(order.status)}
                  </span>
                </td>
                <td>
                  ${order.status === 'PAYMENT_CONFIRMED' ? `
                    <button class="btn-secondary" style="font-size: 12px; color: var(--color-warning);" onclick="window.staffUpdateStatus('${order.orderId}', 'PACKING')">
                      📦 ${t.staffStartPacking}
                    </button>
                  ` : order.status === 'PACKING' ? `
                    <button class="btn-primary" style="font-size: 12px; width: auto; padding: 6px 12px;" onclick="window.staffUpdateStatus('${order.orderId}', 'DISPATCHED_GHTK')">
                      🚚 ${t.staffDispatch}
                    </button>
                  ` : `
                    <span style="font-size: 12px; color: var(--color-success); font-weight: 600;">✓ Đã bàn giao xong</span>
                  `}
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  window.staffUpdateStatus = function (orderId, newStatus) {
    const order = appState.orders.find(o => o.orderId === orderId);
    if (!order) return;
    order.status = newStatus;
    saveState();
    renderApp();
    showToast(`Đơn ${orderId}: Đã chuyển sang ${getStatusText(newStatus)}`);
  };

  // --- MEMBER PROFILE VIEW (FE-01) ---
  function renderProfile(container) {
    const t = i18n[appState.lang];

    container.innerHTML = `
      <div class="profile-card">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; border-bottom: 1px solid var(--color-border); padding-bottom: 16px;">
          <div>
            <h2 style="font-family: var(--font-heading); font-size: 24px; text-transform: uppercase; color: var(--color-primary);">
              ${t.tabProfile}
            </h2>
            <p style="font-size: 13px; color: var(--color-text-secondary);">
              Thông tin được sử dụng tự động cho hành trình mua sắm & thanh toán FE-08.
            </p>
          </div>
          <span class="badge-status badge-paid" style="font-size: 12px;">
            ✓ ${t.verified}
          </span>
        </div>

        <form id="profileForm" onsubmit="window.saveProfileForm(event)">
          <div class="form-group">
            <label class="form-label">${t.fullName}</label>
            <input type="text" id="profFullName" class="form-input" value="${appState.profile.fullName}" required>
          </div>

          <div class="form-group">
            <label class="form-label">${t.email}</label>
            <input type="email" class="form-input" value="${appState.profile.email}" disabled style="opacity: 0.7; cursor: not-allowed;">
          </div>

          <div class="form-group">
            <label class="form-label">${t.phone}</label>
            <input type="tel" id="profPhone" class="form-input" value="${appState.profile.phone}" required>
          </div>

          <div class="form-group">
            <label class="form-label">${t.address}</label>
            <textarea id="profAddress" class="form-input" rows="3" required style="resize: vertical;">${appState.profile.address}</textarea>
          </div>

          <button type="submit" class="btn-primary" style="width: auto;">
            <span>💾</span>
            <span>${t.saveProfile}</span>
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

  // --- INIT BOOTSTRAP ---
  document.addEventListener("DOMContentLoaded", () => {
    renderApp();
  });

})();
