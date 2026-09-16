(() => {
  const STORAGE_KEY = "mland-frontend-prototype-v1";
  const INITIAL_STATE = { language: "vi", session: "", guestCount: "1", designType: "", trackingCode: "" };
  let state = loadState();
  let imageUrl = "";

  const translations = {
    vi: {
      skipLink: "Đi tới nội dung", demoPill: "Prototype · Demo", heroEyebrow: "M.land ring making workshop", heroTitle: "Craft your story into a ring.", heroText: "Chọn buổi workshop, khám phá ý tưởng nhẫn và bắt đầu một kỷ niệm có hình dáng của riêng bạn.", heroCta: "Bắt đầu đặt workshop", heroLearn: "Khám phá hành trình", demoNote: "Bản minh hoạ UX — lịch, giá và quy tắc thật đang chờ M.land xác nhận.", stampTop: "Made with", stampBottom: "your story",
      journeyEyebrow: "A calm, guided journey", journeyTitle: "Từ khoảnh khắc đến chiếc nhẫn", step1Title: "Book", step1Text: "Chọn một buổi workshop minh hoạ.", step2Title: "Imagine", step2Text: "Bắt đầu từ mẫu có sẵn hoặc ý tưởng riêng.", step3Title: "Create", step3Text: "Tự làm tại workshop hoặc để shop thực hiện.", step4Title: "Keep", step4Text: "Theo dõi yêu cầu bằng mã tra cứu demo.",
      bookingEyebrow: "Bước 1 · Workshop", bookingTitle: "Chọn một khoảng thời gian để bắt đầu", bookingText: "Các session dưới đây chỉ minh hoạ giao diện; không phản ánh lịch, sức chứa hoặc availability thực tế.", sessionLegend: "Chọn session demo", morningTitle: "Morning atelier", morningText: "Workshop session · Minh hoạ", afternoonTitle: "Afternoon studio", afternoonText: "Workshop session · Minh hoạ", guestLabel: "Số khách (demo)", emailLabel: "Email để xem validation demo", emailHint: "Không dùng email thật: thông tin này không được lưu hoặc gửi đi.", bookingContinue: "Tiếp tục chọn ý tưởng nhẫn", backHome: "← Về trang chủ",
      designEyebrow: "Bước 2 · Ý tưởng nhẫn", designTitle: "Bạn muốn bắt đầu từ đâu?", ringsOnly: "V1 · Chỉ nhẫn", ringPhotoCaption: "Hình ảnh workshop thực tế · không phải catalogue đã xác nhận", catalogueTitle: "Chọn mẫu nhẫn minh hoạ", catalogueText: "Dành cho ý tưởng bắt đầu từ catalogue đã được xác nhận.", customTitle: "Chia sẻ ý tưởng riêng", customText: "Mô phỏng đường dẫn ảnh tham khảo hoặc custom design cần review.", referenceLabel: "Mô tả ý tưởng (không bắt buộc)", referencePlaceholder: "Ví dụ: một chiếc nhẫn mảnh, có bề mặt hữu cơ…", imageLabel: "Ảnh tham khảo (chỉ preview trong tab)", consentText: "Tôi hiểu đây là demo: ảnh không được gửi tới AI hoặc lưu lại.", customContinue: "Yêu cầu review demo", backBooking: "← Đổi session",
      estimateLabel: "Estimate preview", trackingCardLabel: "Mã tra cứu demo", copyCode: "Sao chép", trackRequest: "Tra cứu yêu cầu", returnHome: "Về trang chủ", trackingEyebrow: "Guest tracking", trackingTitle: "Tra cứu yêu cầu demo", trackingText: "Nhập mã được tạo trong phiên demo này. Không có kết nối đến order thật.", trackingLabel: "Mã tra cứu", trackingSubmit: "Tra cứu", footerText: "M.land customer journey prototype · Không phải booking hoặc catalogue production.", resetDemo: "Xoá trạng thái demo",
      bookingError: "Hãy chọn session demo và nhập một email có định dạng hợp lệ. Không dùng dữ liệu thật.", consentError: "Hãy xác nhận bạn hiểu ảnh không được gửi đi trong demo này.", trackingError: "Mã không khớp với một yêu cầu demo trong trình duyệt này.", copied: "Đã sao chép mã demo.", resetDone: "Đã xoá trạng thái demo.", catalogueEyebrow: "Catalogue path · Demo", catalogueResultTitle: "Ý tưởng nhẫn có thể tiếp tục", catalogueResultDescription: "Đây là trạng thái minh hoạ cho một mẫu từ catalogue đã xác nhận. Staff vẫn sẽ tư vấn các chi tiết cuối cùng.", catalogueEstimate: "Estimate sẽ được M.land xác nhận", catalogueEstimateNote: "Không hiển thị giá demo vì price rule chưa được phê duyệt.", customEyebrow: "Custom path · Demo", customResultTitle: "Đã yêu cầu staff review", customResultDescription: "Ý tưởng custom/reference cần được staff xem xét trước khi có feasibility result hoặc estimate. Không có AI request nào được gửi từ prototype.", customEstimate: "Estimate đang chờ review", customEstimateNote: "Review coverage và response time là Open/TBD.", trackCatalogueStatus: "Demo status", trackCatalogueHeading: "Catalogue idea recorded", trackCatalogueDetail: "Minh hoạ: có thể chuyển sang staff consultation.", trackCustomStatus: "Demo status", trackCustomHeading: "Staff review requested", trackCustomDetail: "Minh hoạ: đang chờ review, không có SLA đã xác nhận."
    },
    en: {
      skipLink: "Skip to content", demoPill: "Prototype · Demo", heroEyebrow: "M.land ring making workshop", heroTitle: "Craft your story into a ring.", heroText: "Choose a workshop, explore a ring idea and begin a keepsake with a shape that feels like yours.", heroCta: "Start a workshop booking", heroLearn: "Explore the journey", demoNote: "UX prototype — real schedule, prices and rules are still awaiting M.land confirmation.", stampTop: "Made with", stampBottom: "your story",
      journeyEyebrow: "A calm, guided journey", journeyTitle: "From a moment to a ring", step1Title: "Book", step1Text: "Choose an illustrative workshop session.", step2Title: "Imagine", step2Text: "Start with an existing sample or your own idea.", step3Title: "Create", step3Text: "Make it at the workshop or let the shop craft it.", step4Title: "Keep", step4Text: "Track the request with a demo code.",
      bookingEyebrow: "Step 1 · Workshop", bookingTitle: "Choose a moment to begin", bookingText: "The sessions below illustrate the interface only; they do not represent real schedules, capacity or availability.", sessionLegend: "Choose a demo session", morningTitle: "Morning atelier", morningText: "Workshop session · Illustration", afternoonTitle: "Afternoon studio", afternoonText: "Workshop session · Illustration", guestLabel: "Guests (demo)", emailLabel: "Email for validation demo", emailHint: "Do not use a real email: this information is not saved or sent.", bookingContinue: "Continue to ring ideas", backHome: "← Back to home",
      designEyebrow: "Step 2 · Ring idea", designTitle: "Where would you like to begin?", ringsOnly: "V1 · Rings only", ringPhotoCaption: "Real workshop photo · not a confirmed catalogue", catalogueTitle: "Choose an illustrative ring sample", catalogueText: "For an idea beginning from a confirmed catalogue.", customTitle: "Share your own idea", customText: "Simulates a reference-image or custom-design path that needs review.", referenceLabel: "Describe the idea (optional)", referencePlaceholder: "For example: a slim ring with an organic texture…", imageLabel: "Reference image (preview in this tab only)", consentText: "I understand this is a demo: the image is not sent to AI or retained.", customContinue: "Request demo review", backBooking: "← Change session",
      estimateLabel: "Estimate preview", trackingCardLabel: "Demo tracking code", copyCode: "Copy", trackRequest: "Track request", returnHome: "Back to home", trackingEyebrow: "Guest tracking", trackingTitle: "Track a demo request", trackingText: "Enter the code created in this demo session. It is not connected to real orders.", trackingLabel: "Tracking code", trackingSubmit: "Track", footerText: "M.land customer journey prototype · Not a production booking or catalogue.", resetDemo: "Clear demo state",
      bookingError: "Choose a demo session and enter a valid email format. Do not use real data.", consentError: "Please confirm that the image is not sent anywhere in this demo.", trackingError: "This code does not match a demo request in this browser.", copied: "Demo code copied.", resetDone: "Demo state cleared.", catalogueEyebrow: "Catalogue path · Demo", catalogueResultTitle: "Your ring idea can continue", catalogueResultDescription: "This illustrates a confirmed-catalogue path. Staff will still advise on final details.", catalogueEstimate: "Estimate will be confirmed by M.land", catalogueEstimateNote: "No demo price is shown because price rules are not yet approved.", customEyebrow: "Custom path · Demo", customResultTitle: "Staff review requested", customResultDescription: "A custom/reference idea needs staff review before a feasibility result or estimate. The prototype sent no request to an AI service.", customEstimate: "Estimate awaits review", customEstimateNote: "Review coverage and response time remain Open/TBD.", trackCatalogueStatus: "Demo status", trackCatalogueHeading: "Catalogue idea recorded", trackCatalogueDetail: "Illustration: ready to continue to staff consultation.", trackCustomStatus: "Demo status", trackCustomHeading: "Staff review requested", trackCustomDetail: "Illustration: awaiting review; no confirmed SLA."
    }
  };

  const $ = (selector) => document.querySelector(selector);
  const $$ = (selector) => [...document.querySelectorAll(selector)];

  function loadState() {
    try {
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
      return { ...INITIAL_STATE, ...pickSafeState(stored) };
    } catch {
      return { ...INITIAL_STATE };
    }
  }

  function pickSafeState(value) {
    return {
      language: value.language === "en" ? "en" : "vi",
      session: ["morning", "afternoon"].includes(value.session) ? value.session : "",
      guestCount: ["1", "2", "3", "4"].includes(value.guestCount) ? value.guestCount : "1",
      designType: ["catalogue", "custom"].includes(value.designType) ? value.designType : "",
      trackingCode: typeof value.trackingCode === "string" && /^MLD-DEMO-\d{4}$/.test(value.trackingCode) ? value.trackingCode : ""
    };
  }

  function saveState() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(pickSafeState(state)));
  }

  function t(key) { return translations[state.language][key] || key; }

  function applyLanguage() {
    document.documentElement.lang = state.language;
    document.title = state.language === "vi" ? "M.land — Workshop làm nhẫn" : "M.land — Ring making workshop";
    $$('[data-i18n]').forEach((element) => { element.textContent = t(element.dataset.i18n); });
    $$('[data-i18n-placeholder]').forEach((element) => { element.placeholder = t(element.dataset.i18nPlaceholder); });
    $("#language-toggle").textContent = state.language === "vi" ? "EN" : "VI";
    $("#language-toggle").setAttribute("aria-label", state.language === "vi" ? "Switch to English" : "Chuyển sang tiếng Việt");
    renderResult();
    renderTracking();
  }

  function showView(id) {
    $$(".view").forEach((view) => { view.hidden = view.id !== id; });
    if (id === "booking") restoreBookingChoice();
    if (id === "design") restoreDesignChoice();
    window.scrollTo({ top: 0, behavior: "smooth" });
    setTimeout(() => $("#" + id).querySelector("h1, h2, input, button")?.focus({ preventScroll: true }), 0);
  }

  function restoreBookingChoice() {
    $("#guest-count").value = state.guestCount;
    const selected = document.querySelector(`input[name="session"][value="${state.session}"]`);
    if (selected) selected.checked = true;
  }

  function restoreDesignChoice() {
    $("#custom-form").hidden = state.designType !== "custom";
  }

  function nextTrackingCode() {
    if (!state.trackingCode) state.trackingCode = `MLD-DEMO-${String(Math.floor(1000 + Math.random() * 9000))}`;
    return state.trackingCode;
  }

  function renderResult() {
    if (!$("#result-title")) return;
    const custom = state.designType === "custom";
    $("#result-icon").textContent = custom ? "⌁" : "✦";
    $("#result-eyebrow").textContent = t(custom ? "customEyebrow" : "catalogueEyebrow");
    $("#result-title").textContent = t(custom ? "customResultTitle" : "catalogueResultTitle");
    $("#result-description").textContent = t(custom ? "customResultDescription" : "catalogueResultDescription");
    $("#estimate-value").textContent = t(custom ? "customEstimate" : "catalogueEstimate");
    $("#estimate-note").textContent = t(custom ? "customEstimateNote" : "catalogueEstimateNote");
    $("#tracking-code").textContent = state.trackingCode || "MLD-DEMO-••••";
  }

  function renderTracking() {
    const result = $("#tracking-result");
    if (result.hidden) return;
    const custom = state.designType === "custom";
    $("#tracking-status").textContent = t(custom ? "trackCustomStatus" : "trackCatalogueStatus");
    $("#tracking-heading").textContent = t(custom ? "trackCustomHeading" : "trackCatalogueHeading");
    $("#tracking-detail").textContent = t(custom ? "trackCustomDetail" : "trackCatalogueDetail");
  }

  function setError(id, message) {
    const target = $("#" + id);
    target.textContent = message;
    target.hidden = !message;
  }

  function showToast(message) {
    const toast = $("#toast");
    toast.textContent = message;
    toast.hidden = false;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 2400);
  }

  $$('[data-view-target]').forEach((button) => button.addEventListener("click", () => showView(button.dataset.viewTarget)));
  $$('[data-scroll-target]').forEach((button) => button.addEventListener("click", () => $("#" + button.dataset.scrollTarget).scrollIntoView({ behavior: "smooth" })));

  $("#language-toggle").addEventListener("click", () => {
    state.language = state.language === "vi" ? "en" : "vi";
    saveState();
    applyLanguage();
  });

  $("#booking-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const session = document.querySelector('input[name="session"]:checked')?.value || "";
    const email = $("#guest-email").value.trim();
    if (!session || !email || !$("#guest-email").validity.valid) {
      setError("booking-error", t("bookingError"));
      return;
    }
    setError("booking-error", "");
    state.session = session;
    state.guestCount = $("#guest-count").value;
    saveState();
    $("#guest-email").value = "";
    showView("design");
  });

  $$('[data-design-type]').forEach((button) => button.addEventListener("click", () => {
    state.designType = button.dataset.designType;
    saveState();
    if (state.designType === "catalogue") {
      nextTrackingCode();
      saveState();
      renderResult();
      showView("result");
    } else {
      $("#custom-form").hidden = false;
      $("#reference-note").focus();
    }
  }));

  $("#reference-image").addEventListener("change", (event) => {
    const file = event.target.files?.[0];
    const preview = $("#image-preview");
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    if (!file) { preview.hidden = true; preview.innerHTML = ""; return; }
    imageUrl = URL.createObjectURL(file);
    preview.innerHTML = `<img alt="${state.language === "vi" ? "Ảnh xem trước cục bộ" : "Local image preview"}">`;
    preview.querySelector("img").src = imageUrl;
    preview.hidden = false;
  });

  $("#custom-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (!$("#image-consent").checked) {
      setError("custom-error", t("consentError"));
      return;
    }
    setError("custom-error", "");
    nextTrackingCode();
    saveState();
    $("#reference-note").value = "";
    $("#reference-image").value = "";
    $("#image-consent").checked = false;
    $("#image-preview").hidden = true;
    $("#image-preview").innerHTML = "";
    if (imageUrl) { URL.revokeObjectURL(imageUrl); imageUrl = ""; }
    renderResult();
    showView("result");
  });

  $("#copy-code").addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(state.trackingCode); showToast(t("copied")); }
    catch { showToast(state.trackingCode); }
  });

  $("#tracking-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const code = $("#tracking-input").value.trim().toUpperCase();
    if (!state.trackingCode || code !== state.trackingCode) {
      $("#tracking-result").hidden = true;
      setError("tracking-error", t("trackingError"));
      return;
    }
    setError("tracking-error", "");
    $("#tracking-result").hidden = false;
    renderTracking();
  });

  $("#reset-demo").addEventListener("click", () => {
    localStorage.removeItem(STORAGE_KEY);
    state = { ...INITIAL_STATE, language: state.language };
    $("#booking-form").reset();
    $("#custom-form").reset();
    $("#tracking-form").reset();
    $("#tracking-result").hidden = true;
    if (imageUrl) { URL.revokeObjectURL(imageUrl); imageUrl = ""; }
    $("#image-preview").hidden = true;
    $("#image-preview").innerHTML = "";
    applyLanguage();
    showToast(t("resetDone"));
    showView("home");
  });

  applyLanguage();
})();
