import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

async function runE2ETest() {
  console.log('--- STARTING E2E RING PRICING VERIFICATION ---');
  const browser = await puppeteer.launch({
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1280,960'],
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 960 });

  const screenshotDir = 'C:\\Users\\admin\\Desktop\\SEP\\mland-operations-hub\\ppswbs-spec\\features\\012-workshop-ring-design-configuration\\test-results\\screenshots';
  if (!fs.existsSync(screenshotDir)) {
    fs.mkdirSync(screenshotDir, { recursive: true });
  }

  try {
    // 1. Navigate to configurator page
    console.log('Navigating to http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK ...');
    await page.goto('http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/configure?mode=BLANK', {
      waitUntil: 'networkidle0',
      timeout: 30000,
    });

    // Wait for the material cards to load
    await page.waitForSelector('.ring-option-card', { timeout: 15000 });

    // 2. Check material card unit prices
    console.log('\n--- Checking Material Card Prices ---');
    const silverText = await page.$eval('[data-testid="material-card-MAT-SILVER-925"]', el => el.textContent.trim());
    const copperText = await page.$eval('[data-testid="material-card-MAT-COPPER"]', el => el.textContent.trim());
    const steelText = await page.$eval('[data-testid="material-card-MAT-STAINLESS-STEEL"]', el => el.textContent.trim());

    console.log('Silver Card Text:', silverText.replace(/\s+/g, ' '));
    console.log('Copper Card Text:', copperText.replace(/\s+/g, ' '));
    console.log('Steel Card Text:', steelText.replace(/\s+/g, ' '));

    if (!silverText.includes('900.000')) {
      throw new Error(`FAIL: Silver card does not show 900.000. Got: ${silverText}`);
    }
    console.log('✓ PASS: Silver card unit price is 900.000 VND');

    if (!copperText.includes('750.000')) {
      throw new Error(`FAIL: Copper card does not show 750.000. Got: ${copperText}`);
    }
    console.log('✓ PASS: Copper card unit price is 750.000 VND');

    if (!steelText.includes('750.000')) {
      throw new Error(`FAIL: Steel card does not show 750.000. Got: ${steelText}`);
    }
    console.log('✓ PASS: Steel card unit price is 750.000 VND');

    // 3. Test Mass > 3.0g for Silver (Default size 8: ~3.50g -> excess 0.50g * 900k = +450.000 đ)
    console.log('\n--- Checking Silver with Mass > 3.0g (Default size 8, 3.50g) ---');
    let weightSummaryText = await page.$eval('[data-testid="weight-summary-value"]', el => el.textContent.trim());
    console.log('Weight summary text:', weightSummaryText);

    if (!weightSummaryText.includes('+60.000')) {
      throw new Error(`FAIL: Expected excess surcharge (+60.000 đ) for Silver ~3.50g. Got: ${weightSummaryText}`);
    }
    console.log('✓ PASS: Excess surcharge +60.000 đ calculated correctly for Silver (~3.50g)');

    // Screenshot excess
    await page.screenshot({ path: path.join(screenshotDir, 'E2E-012-PRICING--configurator-excess-silver.png'), fullPage: true });
    console.log('✓ Saved screenshot: E2E-012-PRICING--configurator-excess-silver.png');

    // 4. Test Copper with Mass > 3.0g (~3.03g -> excess 0.03g * 120k = +3.600 đ)
    console.log('\n--- Switching to Copper ---');
    await page.click('[data-testid="material-card-MAT-COPPER"]');
    await new Promise(r => setTimeout(r, 600));
    weightSummaryText = await page.$eval('[data-testid="weight-summary-value"]', el => el.textContent.trim());
    console.log('Copper weight summary text:', weightSummaryText.replace(/\s+/g, ' '));

    if (!weightSummaryText.includes('+3.600')) {
      throw new Error(`FAIL: Expected excess surcharge (+3.600 đ) for Copper ~3.03g. Got: ${weightSummaryText}`);
    }
    console.log('✓ PASS: Excess surcharge +3.600 đ calculated correctly for Copper (~3.03g)');

    // 5. Test Stainless Steel with Mass <= 3.0g (~2.70g -> in standard limit <= 3.0g)
    console.log('\n--- Switching to Stainless Steel ---');
    await page.click('[data-testid="material-card-MAT-STAINLESS-STEEL"]');
    await new Promise(r => setTimeout(r, 600));
    weightSummaryText = await page.$eval('[data-testid="weight-summary-value"]', el => el.textContent.trim());
    console.log('Steel weight summary text:', weightSummaryText.replace(/\s+/g, ' '));

    if (!weightSummaryText.includes('Định mức ≤ 3.0g')) {
      throw new Error(`FAIL: Expected "(Định mức ≤ 3.0g)" for Steel ~2.70g. Got: ${weightSummaryText}`);
    }
    console.log('✓ PASS: Stainless steel ~2.70g correctly in standard limit (≤ 3.0g, +0 đ)');

    // 6. Test Silver with Mass <= 3.0g (Change size to 6, thickness 1.0mm, width 1.8mm)
    console.log('\n--- Testing Silver with Mass <= 3.0g ---');
    await page.click('[data-testid="material-card-MAT-SILVER-925"]');
    await new Promise(r => setTimeout(r, 400));

    // Click Size 6
    const sizePill = await page.$('[data-testid="size-pill-6"]');
    if (sizePill) {
      await sizePill.click();
      await new Promise(r => setTimeout(r, 400));
    }

    // Set thickness to 1.0 and bandwidth to 1.8 using native input setter so React catches it
    await page.evaluate(() => {
      const setVal = (selector, val) => {
        const input = document.querySelector(selector);
        if (input) {
          const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
          nativeSetter.call(input, val);
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
        }
      };
      setVal('[data-testid="thickness-slider"]', '1.0');
      setVal('[data-testid="bandwidth-slider"]', '1.8');
    });
    await new Promise(r => setTimeout(r, 800));

    weightSummaryText = await page.$eval('[data-testid="weight-summary-value"]', el => el.textContent.trim());
    console.log('Silver slim weight summary text:', weightSummaryText.replace(/\s+/g, ' '));

    if (!weightSummaryText.includes('Định mức ≤ 3.0g')) {
      throw new Error(`FAIL: Expected "(Định mức ≤ 3.0g)" for Silver slim ~0.92g. Got: ${weightSummaryText}`);
    }
    console.log('✓ PASS: Silver slim ring correctly in standard limit (≤ 3.0g) with 0 đ surcharge');

    // Screenshot standard limit
    await page.screenshot({ path: path.join(screenshotDir, 'E2E-012-PRICING--configurator-standard-silver.png'), fullPage: true });
    console.log('✓ Saved screenshot: E2E-012-PRICING--configurator-standard-silver.png');

    console.log('\n======================================================');
    console.log('🎉 ALL E2E ASSERTIONS FOR RING PRICING FORMULA PASSED!');
    console.log('======================================================');

  } catch (err) {
    console.error('❌ E2E TEST FAILED:', err);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
}

runE2ETest();
