import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots');
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function checkMojibake(text, context) {
  const mojibakePattern = /\uFFFD|Ã¡|Ã©|Ã³|Ãº|Ã­|Ã£|áº|á»/g;
  if (mojibakePattern.test(text)) {
    throw new Error(`[MOJIBAKE DETECTED] in ${context}: "${text}"`);
  }
}

async function run() {
  console.log('--- Starting E2E Verification for Scroll, Width Consistency, and Redesign UI ---');
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1400,900'],
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900 });

  try {
    // 1. Authenticate as Customer Member
    console.log('1. Logging in as customer member...');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle0' });
    await page.waitForSelector('input[name="email"], input[type="email"]');
    await page.type('input[name="email"], input[type="email"]', 'customer.test@mland.vn');
    await page.type('input[name="password"], input[type="password"]', 'CustomerTest@123456');
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle0' }).catch(() => { });
    await new Promise(r => setTimeout(r, 1500));

    // 2. Visit Consultation Page & Test Auto-Scroll Bug
    console.log('2. Navigating to /member/consultations...');
    await page.goto('http://localhost:5173/member/consultations', { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 1000));

    // Check window.scrollY immediately after load
    const initialScrollY = await page.evaluate(() => window.scrollY);
    console.log(`Initial window.scrollY on mount: ${initialScrollY}`);
    if (initialScrollY > 10) {
      throw new Error(`Auto-scroll bug failed: window.scrollY is ${initialScrollY}, expected 0 (top of page)!`);
    }

    // Wait 6 seconds (across two 3-4s polling cycles) to verify page doesn't jump
    console.log('Waiting 6 seconds to observe polling stability...');
    await new Promise(r => setTimeout(r, 6000));
    const afterPollScrollY = await page.evaluate(() => window.scrollY);
    console.log(`window.scrollY after 6s of polling: ${afterPollScrollY}`);
    if (afterPollScrollY > 10) {
      throw new Error(`Polling scroll bug: window jumped to ${afterPollScrollY} during polling ticks!`);
    }

    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '01-consultation-top-scroll.png'), fullPage: true });

    // 3. Test Consultation Geometry / Width Consistency
    console.log('3. Inspecting consultation chat layout dimensions...');
    const dimensions = await page.evaluate(() => {
      const layout = document.querySelector('.consultation-layout');
      const sidebar = document.querySelector('.consultation-sidebar');
      const chatMain = document.querySelector('.consultation-chat-main');
      const header = document.querySelector('.consultation-context-header');
      return {
        layoutWidth: layout ? layout.getBoundingClientRect().width : null,
        sidebarWidth: sidebar ? sidebar.getBoundingClientRect().width : null,
        chatMainWidth: chatMain ? chatMain.getBoundingClientRect().width : null,
        headerHeight: header ? header.getBoundingClientRect().height : null,
      };
    });
    console.log('Consultation Dimensions:', JSON.stringify(dimensions, null, 2));

    if (!dimensions.layoutWidth || !dimensions.sidebarWidth || !dimensions.chatMainWidth) {
      throw new Error('Could not find consultation layout elements!');
    }
    // Sidebar should be strictly ~360px
    if (Math.abs(dimensions.sidebarWidth - 360) > 5) {
      throw new Error(`Sidebar width is ${dimensions.sidebarWidth}, expected 360px!`);
    }
    // Header should be fixed ~76px
    if (dimensions.headerHeight && Math.abs(dimensions.headerHeight - 76) > 5) {
      throw new Error(`Header height is ${dimensions.headerHeight}, expected 76px!`);
    }

    // 4. Test Redesigned Custom Design Request UI (/design-requests/new)
    console.log('4. Navigating to /design-requests/new...');
    await page.goto('http://localhost:5173/design-requests/new', { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 1000));

    // Check header and cards
    const pageTitle = await page.$eval('.design-workspace-title', el => el.textContent.trim());
    const eyebrow = await page.$eval('.design-workspace-eyebrow', el => el.textContent.trim());
    console.log(`Page title: "${pageTitle}", Eyebrow: "${eyebrow}"`);
    checkMojibake(pageTitle, 'Page Title');
    checkMojibake(eyebrow, 'Page Eyebrow');

    const cardTitles = await page.$$eval('.design-workspace-card-title', els => els.map(e => e.textContent.trim()));
    console.log('Card titles found:', cardTitles);
    for (const title of cardTitles) {
      checkMojibake(title, 'Card Title');
    }

    // Check dropzone
    const dropzoneText = await page.$eval('.image-dropzone-text', el => el.textContent.trim());
    console.log('Dropzone text:', dropzoneText);
    checkMojibake(dropzoneText, 'Dropzone text');

    // Check submit button
    const submitBtnText = await page.$eval('.btn-luxury-submit', el => el.textContent.trim());
    console.log('Submit button text:', submitBtnText);
    checkMojibake(submitBtnText, 'Submit button');

    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02-redesigned-workspace-ui.png'), fullPage: true });

    console.log('--- ALL E2E CHECKS PASSED SUCCESSFULLY ---');
  } catch (err) {
    console.error('E2E TEST FAILED:', err);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'error-screenshot.png'), fullPage: true }).catch(() => { });
    process.exit(1);
  } finally {
    await browser.close();
  }
}

run();
