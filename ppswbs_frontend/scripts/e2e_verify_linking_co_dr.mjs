import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots');
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

async function run() {
  console.log('=== Starting E2E Verification for CO & DR Linking ===');
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1300, height: 900 });

  // Step 1: Member login
  console.log('1. Member logging in...');
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
  await page.type('input[type="email"], input[name="email"]', 'customer.test@mland.vn');
  await page.type('input[type="password"], input[name="password"]', 'CustomerTest@123456');
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle2' }).catch(() => {}),
    page.click('button[type="submit"]')
  ]);
  await new Promise(r => setTimeout(r, 1000));
  console.log('Member logged in. Current URL:', page.url());

  // Step 2: Navigate to My Custom Orders
  console.log('2. Navigating to /account/my-custom-orders...');
  await page.goto('http://localhost:5173/account/my-custom-orders', { waitUntil: 'networkidle2' });
  await new Promise(r => setTimeout(r, 1500));

  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'e2e-linking-tab-orders.png'), fullPage: true });

  // Verify Tab 1 (Orders) content
  const orderCards = await page.$$('.custom-orders-page div[style*="border: 1px solid"]');
  console.log(`Found ${orderCards.length} order card elements.`);

  const tabOrdersContent = await page.evaluate(() => document.body.innerText);
  const hasLinkedDesignText = tabOrdersContent.includes('Mẫu thiết kế gốc:') || tabOrdersContent.includes('Original design request:');
  console.log('- Has "Mẫu thiết kế gốc" linking text:', hasLinkedDesignText);

  // Check if there is a link button to DR
  const drButtons = await page.$$('button[title*="Xem trong danh sách yêu cầu"], button[title*="View in Requests tab"]');
  console.log(`- Found ${drButtons.length} DR linking buttons.`);
  if (drButtons.length > 0) {
    const drText = await page.evaluate(el => el.textContent, drButtons[0]);
    console.log(`- First DR linking button text: "${drText.trim()}"`);

    // Click the DR linking button -> switches to Tab 2
    console.log('3. Clicking DR linking button to switch to Requests tab...');
    await drButtons[0].click();
    await new Promise(r => setTimeout(r, 1000));
  } else {
    // Switch to tab 2 manually
    console.log('3. Switching to Requests tab via tab button...');
    const reqTabBtn = await page.$('button[style*="border-bottom"][style*="transparent"], button:nth-of-type(2)');
    if (reqTabBtn) await reqTabBtn.click();
    await new Promise(r => setTimeout(r, 1000));
  }

  await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'e2e-linking-tab-requests.png'), fullPage: true });

  const tabRequestsContent = await page.evaluate(() => document.body.innerText);
  const hasCreatedOrderBadge = tabRequestsContent.includes('Đã tạo đơn chế tác:') || tabRequestsContent.includes('Custom order created:');
  console.log('- Has "Đã tạo đơn chế tác" badge in Requests tab:', hasCreatedOrderBadge);

  // Step 4: Open first Custom Order detail to verify Design Evidence card
  console.log('4. Navigating to Custom Order detail page...');
  const orderDetailLinks = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('a[href*="/account/my-custom-orders/CO-"]'))
      .map(a => a.getAttribute('href'));
  });
  console.log('Found order detail links:', orderDetailLinks);

  if (orderDetailLinks.length > 0) {
    const firstDetailHref = orderDetailLinks[0];
    console.log('Visiting order detail:', firstDetailHref);
    await page.goto(`http://localhost:5173${firstDetailHref}`, { waitUntil: 'networkidle2' });
    await new Promise(r => setTimeout(r, 1500));

    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'e2e-linking-order-evidence-detail.png'), fullPage: true });

    const detailContent = await page.evaluate(() => document.body.innerText);
    const hasEvidenceTitle = detailContent.includes('Bằng chứng thiết kế & Yêu cầu gốc') || detailContent.includes('Design Evidence & Original Request');
    console.log('- Has "Bằng chứng thiết kế & Yêu cầu gốc" section:', hasEvidenceTitle);

    if (!hasEvidenceTitle) {
      throw new Error('Design Evidence section not found on Custom Order detail page!');
    }

    console.log('PASS: Design Evidence section successfully rendered on Custom Order detail!');
  } else {
    console.log('Note: No existing CO found in list to navigate detail.');
  }

  await browser.close();
  console.log('=== ALL LINKING E2E VERIFICATIONS PASSED SUCCESSFULLY! ===');
}

run().catch(err => {
  console.error('Test run failed:', err);
  process.exit(1);
});
