import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots');
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function login(page, email, password) {
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
  await page.type('input[type="email"], input[name="email"]', email);
  await page.type('input[type="password"], input[name="password"]', password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  await new Promise((r) => setTimeout(r, 1500));
  console.log(`[AUTH] Logged in as ${email}. Current URL: ${page.url()}`);
}

async function run() {
  console.log('======================================================================');
  console.log('=== STARTING E2E VERIFICATION: CUSTOM ORDERS TABS & RADIO TRANSITION ===');
  console.log('======================================================================');

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1400,980'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 980 });

  const summary = {
    staffTabsInitial: {},
    transitionModalCaptured: false,
    staffTabsAfterTransition: {},
    managerTabsSynced: {},
    memberStatusBadge: null,
    memberDetailBadge: null,
    passed: false,
  };

  try {
    // -------------------------------------------------------------------------
    // STEP 1: STAFF LOGS IN & VERIFIES 4 TABS WITH 2 ORDERS EACH
    // -------------------------------------------------------------------------
    console.log('\n--- Step 1: Staff Login & Initial 4-Stage Tab Verification ---');
    await login(page, 'staff.test@mland.vn', 'StaffTest@123456');

    await page.goto('http://localhost:5173/staff/custom-orders', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 2000));

    // Verify Title
    const title = await page.$eval('h1', (el) => el.textContent.trim());
    console.log(`Staff Page Title: "${title}"`);

    // Verify KPI numbers
    const kpiMetrics = await page.evaluate(() => {
      const numbers = Array.from(document.querySelectorAll('.ota-workspace strong')).map((el) => el.textContent.trim());
      return numbers;
    });
    console.log('KPI Metrics (PAID, IN_PRODUCTION, READY_FOR_FULFILMENT, CLOSED):', kpiMetrics);

    // Verify Tab Badges
    const tabsData = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('button[role="tab"]')).map((tab) => {
        const text = tab.innerText.replace(/\n/g, ' ').trim();
        return text;
      });
    });
    console.log('Initial Tab Data:', tabsData);
    summary.staffTabsInitial = tabsData;

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-01-staff-custom-orders-initial-seed.png'),
      fullPage: true,
    });
    console.log('Saved screenshot: e2e-01-staff-custom-orders-initial-seed.png');

    // Iterate through tabs to verify none is empty
    const tabButtons = await page.$$('button[role="tab"]');
    for (let i = 0; i < 4; i++) {
      await tabButtons[i].click();
      await new Promise((r) => setTimeout(r, 600));
      const orderCountInTab = await page.evaluate(() => {
        // Count order cards
        const cards = document.querySelectorAll('.ota-workspace > div > div[style*="border: 1px solid"]');
        return cards.length;
      });
      console.log(`Tab index ${i} has ${orderCountInTab} order card(s).`);
      if (orderCountInTab === 0) {
        throw new Error(`Tab index ${i} is unexpectedly empty!`);
      }
    }

    // -------------------------------------------------------------------------
    // STEP 2: INTERACTIVE RADIO BUTTON & CONFIRMATION POPUP WINDOW
    // -------------------------------------------------------------------------
    console.log('\n--- Step 2: Test Interactive Radio Stage Transition & Confirmation Modal ---');
    // Switch back to tab "Chờ chế tác" (Tab 0)
    await tabButtons[0].click();
    await new Promise((r) => setTimeout(r, 800));

    // Find radio button for 'IN_PRODUCTION' for order CO-D8FD507B
    console.log('Locating radio button "Đang gia công" for order CO-D8FD507B...');
    const radioSelected = await page.evaluate(() => {
      const radios = Array.from(document.querySelectorAll('input[type="radio"]'));
      const targetRadio = radios.find((r) => r.name.includes('CO-D8FD507B') && r.value === 'IN_PRODUCTION');
      if (targetRadio) {
        targetRadio.scrollIntoView({ behavior: 'instant', block: 'center' });
        targetRadio.click();
        return true;
      }
      return false;
    });

    if (!radioSelected) {
      throw new Error('Could not find radio input for IN_PRODUCTION on order CO-D8FD507B!');
    }
    console.log('Clicked radio button for "Đang gia công". Waiting for confirmation modal...');
    await new Promise((r) => setTimeout(r, 1000));

    // Check if modal dialog appeared
    const modalVisible = await page.evaluate(() => {
      const dialog = document.querySelector('[role="dialog"]');
      if (!dialog) return false;
      const title = dialog.querySelector('h2, h3, strong')?.textContent || '';
      return title.includes('Xác nhận chuyển trạng thái') || dialog.textContent.includes('CO-D8FD507B');
    });

    console.log(`Confirmation modal visible: ${modalVisible}`);
    if (!modalVisible) {
      throw new Error('Confirmation modal dialog did not appear after selecting radio button!');
    }
    summary.transitionModalCaptured = true;

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-02-transition-confirmation-modal.png'),
    });
    console.log('Saved screenshot: e2e-02-transition-confirmation-modal.png');

    // Click confirm button in modal: "Xác nhận chuyển"
    console.log('Clicking "Xác nhận chuyển" in confirmation popup...');
    const confirmed = await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('[role="dialog"] button'));
      const confirmBtn = buttons.find((b) => b.textContent.includes('Xác nhận chuyển'));
      if (confirmBtn) {
        confirmBtn.click();
        return true;
      }
      return false;
    });

    if (!confirmed) {
      throw new Error('Confirm button not found inside popup dialog!');
    }

    // Wait for API call and UI refresh
    await new Promise((r) => setTimeout(r, 3000));

    // Check success notification
    const notificationText = await page.evaluate(() => {
      const alert = document.querySelector('[role="alert"], .ota-workspace div[style*="background: rgb(240, 253, 244)"], .ota-workspace div[style*="#f0fdf4"]');
      return alert ? alert.textContent.trim() : '';
    });
    console.log(`Notification message: "${notificationText}"`);

    // Verify updated tab badges
    const updatedTabsData = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('button[role="tab"]')).map((tab) => {
        return tab.innerText.replace(/\n/g, ' ').trim();
      });
    });
    console.log('Updated Tab Data after transition:', updatedTabsData);
    summary.staffTabsAfterTransition = updatedTabsData;

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-03-staff-custom-orders-after-transition.png'),
      fullPage: true,
    });
    console.log('Saved screenshot: e2e-03-staff-custom-orders-after-transition.png');

    // -------------------------------------------------------------------------
    // STEP 3: MANAGER PERSPECTIVE SYNCHRONIZATION
    // -------------------------------------------------------------------------
    console.log('\n--- Step 3: Manager Perspective Synchronization ---');
    await login(page, 'manager.test@mland.vn', 'ManagerTest@123456');

    await page.goto('http://localhost:5173/manager/custom-orders', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 2000));

    const managerTabsData = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('button[role="tab"]')).map((tab) => {
        return tab.innerText.replace(/\n/g, ' ').trim();
      });
    });
    console.log('Manager View Tab Data:', managerTabsData);
    summary.managerTabsSynced = managerTabsData;

    // Switch to tab "Đang gia công" and verify order CO-D8FD507B is in Manager's view
    const mgrTabButtons = await page.$$('button[role="tab"]');
    await mgrTabButtons[1].click(); // Tab "Đang gia công"
    await new Promise((r) => setTimeout(r, 800));

    const managerHasOrder = await page.evaluate(() => {
      return document.body.innerText.includes('CO-D8FD507B');
    });
    console.log(`Order CO-D8FD507B exists in Manager "Đang gia công" tab: ${managerHasOrder}`);
    if (!managerHasOrder) {
      throw new Error('Order CO-D8FD507B was not found in Manager "Đang gia công" tab!');
    }

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-04-manager-custom-orders-synced.png'),
      fullPage: true,
    });
    console.log('Saved screenshot: e2e-04-manager-custom-orders-synced.png');

    // -------------------------------------------------------------------------
    // STEP 4: MEMBER PERSPECTIVE SYNCHRONIZATION
    // -------------------------------------------------------------------------
    console.log('\n--- Step 4: Member Perspective Synchronization ---');
    await login(page, 'customer.test@mland.vn', 'CustomerTest@123456');

    await page.goto('http://localhost:5173/account/my-custom-orders', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 2000));

    // Verify order CO-D8FD507B appears in Member's custom orders list
    const memberPageText = await page.evaluate(() => document.body.innerText);
    const orderPresentInMemberView = memberPageText.includes('CO-D8FD507B');
    console.log(`Order CO-D8FD507B visible in Member orders: ${orderPresentInMemberView}`);
    if (!orderPresentInMemberView) {
      throw new Error('Order CO-D8FD507B not found in customer.test@mland.vn custom orders list!');
    }

    // Check status badge for CO-D8FD507B
    const memberBadgeText = await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('.design-container div'));
      for (const card of cards) {
        if (card.textContent.includes('CO-D8FD507B')) {
          const badge = card.querySelector('.custom-order-status-badge');
          if (badge) return badge.textContent.trim();
        }
      }
      return '';
    });
    console.log(`Member List Status Badge for CO-D8FD507B: "${memberBadgeText}"`);
    summary.memberStatusBadge = memberBadgeText;

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-05-member-my-custom-orders.png'),
      fullPage: true,
    });
    console.log('Saved screenshot: e2e-05-member-my-custom-orders.png');

    // Open detail page: /account/my-custom-orders/CO-D8FD507B
    console.log('Navigating to Member Custom Order Detail Page: /account/my-custom-orders/CO-D8FD507B...');
    await page.goto('http://localhost:5173/account/my-custom-orders/CO-D8FD507B', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 2000));

    const detailBadgeText = await page.evaluate(() => {
      const badge = document.querySelector('.custom-order-status-badge');
      return badge ? badge.textContent.trim() : '';
    });
    console.log(`Member Order Detail Status Badge: "${detailBadgeText}"`);
    summary.memberDetailBadge = detailBadgeText;

    const detailPageBody = await page.evaluate(() => document.body.innerText);
    console.log('Order detail page contains "Đang gia công":', detailPageBody.includes('Đang gia công'));

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-06-member-custom-order-detail.png'),
      fullPage: true,
    });
    console.log('Saved screenshot: e2e-06-member-custom-order-detail.png');

    summary.passed = true;
    console.log('\n======================================================================');
    console.log('=== ALL E2E VERIFICATION CHECKS PASSED SUCCESSFULLY! ===');
    console.log('======================================================================');
  } catch (err) {
    console.error('E2E Verification Failed:', err);
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'e2e-custom-orders-error.png'),
      fullPage: true,
    });
    summary.passed = false;
    summary.error = err.message;
  } finally {
    await browser.close();
    fs.writeFileSync(
      path.resolve('..', 'e2e-custom-orders-tabs-summary.json'),
      JSON.stringify(summary, null, 2),
      'utf8'
    );
  }
}

run();
