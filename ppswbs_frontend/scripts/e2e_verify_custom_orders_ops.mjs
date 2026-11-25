import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots');
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function run() {
  console.log('=== Starting E2E Verification for Staff & Manager Custom Order Operations ===');
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1350, height: 950 });

  try {
    // ----------------------------------------------------
    // TEST 1: Staff Login & Operations Shell Navigation
    // ----------------------------------------------------
    console.log('--- Step 1: Staff logging in ---');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
    await page.type('input[type="email"], input[name="email"]', 'staff.test@mland.vn');
    await page.type('input[type="password"], input[name="password"]', 'StaffTest@123456');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}),
      page.click('button[type="submit"]'),
    ]);
    await new Promise((r) => setTimeout(r, 1500));
    console.log('Staff logged in. Current URL:', page.url());

    // Step 2: Navigate to /staff/custom-orders
    console.log('--- Step 2: Navigating to /staff/custom-orders ---');
    await page.goto('http://localhost:5173/staff/custom-orders', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 2000));
    console.log('Navigated to Custom Orders Operations. Current URL:', page.url());

    // Step 3: Verify Page Elements
    console.log('--- Step 3: Verifying Staff Custom Orders Operations Screen UI ---');
    const pageHeading = await page.waitForSelector('h1');
    const headingText = await page.evaluate((el) => el.textContent, pageHeading);
    console.log('Page Title:', headingText.trim());

    // Check Stage Tabs
    const tabs = await page.$$('button[role="tab"]');
    console.log(`Found ${tabs.length} stage tabs.`);
    const tabTexts = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('button[role="tab"]')).map((t) => t.textContent.trim());
    });
    console.log('Stage tabs present:', tabTexts);

    // Check KPI summary cards
    const kpiElements = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('.wsp-workspace strong')).map((s) => s.textContent.trim());
    });
    console.log('KPI metric numbers displayed:', kpiElements);

    // Save screenshot of Staff Custom Orders view
    const staffScreenshotPath = path.join(SCREENSHOT_DIR, 'e2e-staff-custom-orders-ops.png');
    await page.screenshot({ path: staffScreenshotPath, fullPage: true });
    console.log('Saved staff screenshot to:', staffScreenshotPath);

    // Test tab switching
    console.log('--- Step 4: Testing Tab Switching ---');
    for (const tabEl of tabs.slice(0, 4)) {
      const tabName = await page.evaluate((t) => t.textContent.trim(), tabEl);
      await tabEl.click();
      await new Promise((r) => setTimeout(r, 400));
      console.log(`Switched to tab: "${tabName}"`);
    }

    // Step 5: Test Topbar User Menu on Public pages (e.g. /workshops)
    console.log('--- Step 5: Checking Topbar User Menu on Public Page ---');
    await page.goto('http://localhost:5173/workshops', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 1500));

    const userChip = await page.waitForSelector('.main-topbar-user-chip');
    await userChip.click();
    await new Promise((r) => setTimeout(r, 600));

    const dropdownText = await page.evaluate(() => {
      const dd = document.querySelector('.main-topbar-dropdown');
      return dd ? dd.innerText : '';
    });
    console.log('Dropdown contains "Quản lý đơn gia công":', dropdownText.includes('Quản lý đơn gia công'));
    if (!dropdownText.includes('Quản lý đơn gia công')) {
      throw new Error('Menu item "Quản lý đơn gia công" not found in user dropdown!');
    }

    // Click the menu item to return to /staff/custom-orders
    const customOrdersLink = await page.evaluateHandle(() => {
      const items = Array.from(document.querySelectorAll('.main-topbar-dropdown-item'));
      return items.find((el) => el.textContent.includes('Quản lý đơn gia công'));
    });
    if (customOrdersLink) {
      await customOrdersLink.click();
      await new Promise((r) => setTimeout(r, 1500));
      console.log('Successfully navigated back via Topbar menu item. Current URL:', page.url());
    }

    // ----------------------------------------------------
    // TEST 2: Manager View & Permissions Check
    // ----------------------------------------------------
    console.log('--- Step 6: Manager logging in ---');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
    await page.type('input[type="email"], input[name="email"]', 'manager.test@mland.vn');
    await page.type('input[type="password"], input[name="password"]', 'ManagerTest@123456');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}),
      page.click('button[type="submit"]'),
    ]);
    await new Promise((r) => setTimeout(r, 1500));
    console.log('Manager logged in. Current URL:', page.url());

    console.log('--- Step 7: Navigating to /manager/custom-orders ---');
    await page.goto('http://localhost:5173/manager/custom-orders', { waitUntil: 'networkidle2' });
    await new Promise((r) => setTimeout(r, 1500));

    const managerHeading = await page.waitForSelector('h1');
    const mgrHeadingText = await page.evaluate((el) => el.textContent, managerHeading);
    console.log('Manager Page Title:', mgrHeadingText.trim());

    const managerScreenshotPath = path.join(SCREENSHOT_DIR, 'e2e-manager-custom-orders-ops.png');
    await page.screenshot({ path: managerScreenshotPath, fullPage: true });
    console.log('Saved manager screenshot to:', managerScreenshotPath);

    console.log('=== E2E Custom Order Operations Verification SUCCESSFUL! ===');
  } catch (err) {
    console.error('E2E Verification Error:', err);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'e2e-custom-orders-ops-error.png'), fullPage: true });
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
}

run();
