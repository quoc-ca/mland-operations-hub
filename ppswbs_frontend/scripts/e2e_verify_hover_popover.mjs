import puppeteer from 'puppeteer-core';
import path from 'path';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('e2e-screenshots');

async function verifyHover() {
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1280,900'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });

  console.log('Logging in...');
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
  await page.type('input[type="email"]', 'customer.test@mland.vn');
  await page.type('input[type="password"]', 'CustomerTest@123456');
  await page.click('button[type="submit"]');
  await new Promise(r => setTimeout(r, 2500));

  // Open consultation with attached ring query param
  console.log('Navigating to consultation with attached ring query param...');
  await page.goto('http://localhost:5173/member/consultations?designReference=DSG-250aa6fe-d86d-4e40-b505-0f0953845a13&designVersion=1', { waitUntil: 'networkidle2' });
  await new Promise(r => setTimeout(r, 3000));

  console.log('Consultation URL:', page.url());
  const hoverTrigger = await page.$('[data-testid="attached-ring-hover-trigger"]');
  console.log('Hover trigger found:', Boolean(hoverTrigger));

  if (hoverTrigger) {
    await hoverTrigger.hover();
    await new Promise(r => setTimeout(r, 800));
    const popover = await page.$('[data-testid="attached-ring-recap-popover"]');
    console.log('Popover rendered on hover:', Boolean(popover));
    if (popover) {
      const text = await page.evaluate(el => el.innerText, popover);
      console.log('Popover content:\n' + text);
      const hasSvg = Boolean(await page.$('[data-testid="attached-ring-recap-popover"] svg'));
      console.log('SVG graphic present:', hasSvg);
      await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04_attached_ring_hover_recap_popover.png') });
      console.log('✅ Screenshot saved to 04_attached_ring_hover_recap_popover.png');
    }
  } else {
    console.log('Page body snippet:', (await page.evaluate(() => document.body.innerText)).substring(0, 300));
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04_consultation_snapshot.png') });
  }

  await browser.close();
}

verifyHover().catch(console.error);
