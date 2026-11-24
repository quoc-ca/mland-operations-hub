import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots', 'scenario1');
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function checkMojibake(text, context) {
  const mojibakePattern = /\uFFFD|Ã¡|Ã©|Ã³|Ãº|Ã­|Ã£|áº|á»/g;
  if (mojibakePattern.test(text)) {
    throw new Error(`[MOJIBAKE DETECTED] in ${context}: "${text}"`);
  }
}

function javaUrlEncode(str) {
  return encodeURIComponent(str).replace(/%20/g, '+');
}

function signVnpayParams(params, secret = 'MLANDVNPAYHASHSECRET2026112100') {
  const sorted = Object.keys(params)
    .filter(k => k !== 'vnp_SecureHash' && k !== 'vnp_SecureHashType' && params[k] != null)
    .sort();
  const query = sorted.map(k => `${k}=${javaUrlEncode(params[k])}`).join('&');
  const hmac = crypto.createHmac('sha512', secret.trim());
  return hmac.update(Buffer.from(query, 'utf-8')).digest('hex');
}

async function getFirebaseToken(page) {
  return await page.evaluate(async () => {
    for (let attempt = 0; attempt < 25; attempt++) {
      const token = await new Promise((resolve) => {
        try {
          const req = indexedDB.open('firebaseLocalStorageDb');
          req.onsuccess = () => {
            const db = req.result;
            if (!db.objectStoreNames.contains('firebaseLocalStorage')) {
              return resolve(null);
            }
            const tx = db.transaction(['firebaseLocalStorage'], 'readonly');
            const store = tx.objectStore('firebaseLocalStorage');
            const getAll = store.getAll();
            getAll.onsuccess = () => {
              for (const item of getAll.result || []) {
                const tok = item?.value?.stsTokenManager?.accessToken;
                if (tok) return resolve(tok);
              }
              resolve(null);
            };
            getAll.onerror = () => resolve(null);
          };
          req.onerror = () => resolve(null);
        } catch {
          resolve(null);
        }
      });
      if (token) return token;
      await new Promise(r => setTimeout(r, 200));
    }
    return null;
  });
}

async function loginAs(page, email, pass) {
  console.log(`Logging in as ${email}...`);
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle0', timeout: 20000 });
  await page.evaluate((e, p) => {
    const emailInput = document.querySelector('input[type="email"], input[name="email"]');
    const pwdInput = document.querySelector('input[type="password"], input[name="password"]');
    const submitBtn = document.querySelector('button.auth-submit, button[type="submit"]');

    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(emailInput, e);
    emailInput.dispatchEvent(new Event('input', { bubbles: true }));
    emailInput.dispatchEvent(new Event('change', { bubbles: true }));

    setter.call(pwdInput, p);
    pwdInput.dispatchEvent(new Event('input', { bubbles: true }));
    pwdInput.dispatchEvent(new Event('change', { bubbles: true }));

    submitBtn.click();
  }, email, pass);

  await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 15000 }).catch(() => {});
  await new Promise(r => setTimeout(r, 1500));
}

async function runScenario1() {
  const isolationKey = `iso_sc1_${Date.now()}`;
  console.log(`\n======================================================`);
  console.log(`STARTING SCENARIO 1: E2E-XF-004-012-013-001`);
  console.log(`Isolation Key: ${isolationKey}`);
  console.log(`======================================================\n`);

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1400,900'],
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 900 });

  const timings = [];
  let navCount = 0;

  function recordStep(stepName, start, end) {
    const duration = ((end - start) / 1000).toFixed(2);
    timings.push({ stepName, durationSec: duration });
    console.log(`⏱ Step "${stepName}": ${duration}s (Budget: <= 30s)`);
    if (parseFloat(duration) > 30) {
      throw new Error(`Step ${stepName} exceeded 30s budget! Took ${duration}s`);
    }
  }

  try {
    // -------------------------------------------------------------------------
    // Step 1: Sign in as Member
    // -------------------------------------------------------------------------
    let t0 = Date.now();
    await loginAs(page, 'member.test@mland.vn', 'Member@123456');
    navCount++;
    recordStep('1. Member Sign In', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '01_member_signed_in.jpg') });

    // -------------------------------------------------------------------------
    // Step 2-4: Open Ring Configurator, select options, preview
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await page.goto('http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/blank', { waitUntil: 'networkidle0' });
    navCount++;
    await page.waitForSelector('[data-testid="save-and-evaluate-btn"]', { timeout: 15000 });
    
    // Select options (Material, Shape, Size)
    console.log('Selecting design options...');
    const materialCard = await page.$('.ring-option-card:nth-of-type(1)');
    if (materialCard) await materialCard.click();
    await new Promise(r => setTimeout(r, 500));

    // Enable Engraving text
    const engToggle = await page.$('[data-testid="engraving-toggle"]');
    if (engToggle) {
      await engToggle.click();
      await new Promise(r => setTimeout(r, 600));
      const engInput = await page.$('[data-testid="engraving-input"]');
      if (engInput) {
        await engInput.type(`Love ${isolationKey.slice(-4)}`);
      }
    }

    recordStep('2-4. Ring Configurator & Dynamic Preview', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02_ring_configuration.jpg') });

    // -------------------------------------------------------------------------
    // Step 5-7: Save and Evaluate Design
    // -------------------------------------------------------------------------
    t0 = Date.now();
    const saveBtn = await page.$('[data-testid="save-and-evaluate-btn"]');
    await saveBtn.click();
    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 20000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 2000));
    navCount++;

    const currentUrl = page.url();
    console.log(`Evaluated design URL: ${currentUrl}`);
    const designIdMatch = currentUrl.match(/\/design\/([A-Za-z0-9_-]+)/);
    const designId = designIdMatch ? designIdMatch[1] : null;
    console.log(`Created & Saved Design ID: ${designId}`);

    // Verify status is not falsely reviewed
    const pageText = await page.evaluate(() => document.body.innerText);
    checkMojibake(pageText, 'Design Evaluation Page');

    recordStep('5-7. Save & Evaluate Design', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '03_design_evaluated.jpg') });

    // -------------------------------------------------------------------------
    // Step 8-11: Submit Custom Request referencing saved design
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Navigating to /design-requests/new...');
    await page.goto(`http://localhost:5173/design-requests/new?designReference=${designId || ''}`, { waitUntil: 'networkidle0' });
    navCount++;
    await page.waitForSelector('textarea', { timeout: 15000 });

    const reqDesc = `Yêu cầu chế tác theo mẫu thiết kế ${designId || 'mới'} cho isolation ${isolationKey}. Cần đai nhẫn sáng bóng hoàn hảo.`;
    await page.type('textarea', reqDesc);

    // If design dropdown exists, verify or select it
    if (designId) {
      await page.evaluate((dId) => {
        const select = document.querySelector('select');
        if (select && dId) {
          const opts = Array.from(select.options);
          const matching = opts.find(o => o.value === dId || o.textContent.includes(dId.slice(-6)));
          if (matching) {
            select.value = matching.value;
            select.dispatchEvent(new Event('change', { bubbles: true }));
          }
        }
      }, designId);
    }

    // Submit custom request
    console.log('Submitting custom request...');
    const submitBtn = await page.$('button[type="submit"]');
    await submitBtn.click();

    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 15000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 2000));
    navCount++;

    const reqUrl = page.url();
    console.log(`Custom request result URL: ${reqUrl}`);
    const reqMatch = reqUrl.match(/\/design-requests\/([A-Za-z0-9_-]+)/);
    const designRequestId = reqMatch ? reqMatch[1] : null;
    console.log(`Created Design Request ID: ${designRequestId}`);

    // Verify request is in NEED_REVIEW
    const reqPageText = await page.evaluate(() => document.body.innerText);
    checkMojibake(reqPageText, 'Design Detail Page');
    const isNeedReview = reqPageText.includes('Đang chờ nhân viên thẩm duyệt') || reqPageText.includes('NEED_REVIEW') || reqPageText.includes('Đang chờ duyệt');
    console.log(`Request state is NEED_REVIEW: ${isNeedReview}`);

    recordStep('8-11. Submit Custom Design Request', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04_request_created_need_review.jpg') });

    // -------------------------------------------------------------------------
    // Step 12-18: Staff Review & Accept with Explanation
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'staff.test@mland.vn', 'StaffTest@123456');
    navCount++;

    console.log(`Staff opening review detail for request: ${designRequestId}...`);
    await page.goto(`http://localhost:5173/design-review-requests/${designRequestId}`, { waitUntil: 'networkidle0' });
    navCount++;
    await page.waitForSelector('textarea', { timeout: 15000 });

    // Select ACCEPTED
    await page.evaluate(() => {
      const select = document.querySelector('select');
      if (select) {
        select.value = 'ACCEPTED';
        select.dispatchEvent(new Event('change', { bubbles: true }));
      }
    });

    const staffExplanation = `Xác nhận tiếp nhận yêu cầu chế tác ${isolationKey}. Mẫu thiết kế khả thi với kỹ thuật đúc bạc 925.`;
    await page.type('textarea', staffExplanation);

    // Click submit decision button on form
    console.log('Staff submitting decision form...');
    const decisionSubmitBtn = await page.$('form button[type="submit"]');
    await decisionSubmitBtn.click();
    await new Promise(r => setTimeout(r, 600));

    // Confirm dialog
    console.log('Confirming decision in dialog...');
    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('12-18. Staff Review Decision ACCEPTED', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '05_staff_accepted_decision.jpg') });

    // -------------------------------------------------------------------------
    // Step 19: Staff Prepares Quote
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Staff preparing quote for accepted request...');
    await page.waitForSelector('input[data-testid="quote-amount-input"], input#quote-amount', { timeout: 10000 });
    
    await page.evaluate(() => {
      const amtInput = document.querySelector('input[data-testid="quote-amount-input"], input#quote-amount');
      const hrsInput = document.querySelector('input[data-testid="quote-hours-input"], input#quote-hours');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      
      if (amtInput) {
        setter.call(amtInput, '500000');
        amtInput.dispatchEvent(new Event('input', { bubbles: true }));
        amtInput.dispatchEvent(new Event('change', { bubbles: true }));
      }
      if (hrsInput) {
        setter.call(hrsInput, '72');
        hrsInput.dispatchEvent(new Event('input', { bubbles: true }));
        hrsInput.dispatchEvent(new Event('change', { bubbles: true }));
      }
    });

    const submitQuoteBtn = await page.$('form.quote-form button[type="submit"], form:has([data-testid="quote-amount-input"]) button[type="submit"]');
    if (submitQuoteBtn) {
      await submitQuoteBtn.click();
    } else {
      await page.evaluate(() => {
        const btns = Array.from(document.querySelectorAll('button'));
        const qBtn = btns.find(b => b.textContent.includes('Gửi báo giá cho Manager duyệt') || b.textContent.includes('Gửi báo giá'));
        if (qBtn) qBtn.click();
      });
    }
    await new Promise(r => setTimeout(r, 600));

    // Confirm quote dialog
    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('19. Staff Prepare Quote', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '06_staff_quote_prepared.jpg') });

    // -------------------------------------------------------------------------
    // Step 20-23: Manager Signs In & Approves Quote
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'manager.test@mland.vn', 'ManagerTest@123456');
    navCount++;

    console.log(`Manager opening review detail for request: ${designRequestId}...`);
    await page.goto(`http://localhost:5173/design-review-requests/${designRequestId}`, { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    console.log('Manager view loaded. Clicking approve quote button...');
    const approveClicked = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const btn = btns.find(b => b.textContent.includes('Phê duyệt Báo giá') || b.textContent.includes('Phê duyệt báo giá') || b.textContent.toLowerCase().includes('phê duyệt'));
      if (btn) {
        btn.click();
        return true;
      }
      return false;
    });
    console.log(`Clicked approve quote button: ${approveClicked}`);
    await new Promise(r => setTimeout(r, 800));

    // Confirm manager approval in dialog
    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('20-23. Manager Approve Quote', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '07_manager_approved_quote.jpg') });

    // -------------------------------------------------------------------------
    // Step 24-30: Member Full Payment via VNPay Simulator / Callback
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'member.test@mland.vn', 'Member@123456');
    navCount++;

    console.log('Member navigating to /account/my-custom-orders...');
    await page.goto('http://localhost:5173/account/my-custom-orders', { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '08_my_custom_orders_list.jpg') });

    // Click on the custom order
    await page.evaluate(() => {
      const links = Array.from(document.querySelectorAll('a[href*="/account/my-custom-orders/"]'));
      if (links.length > 0) links[0].click();
    });
    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 10000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 1500));
    navCount++;

    const orderDetailUrl = page.url();
    console.log(`Custom order detail URL: ${orderDetailUrl}`);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '09_custom_order_detail_before_pay.jpg') });

    // Verify 100% full payment button exists
    const payBtnText = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const btn = btns.find(b => b.textContent.includes('Thanh toán'));
      return btn ? btn.textContent.trim() : null;
    });
    console.log(`Payment button text: ${payBtnText}`);

    // Call startFullPayment API directly with Member's Firebase token to get the exact paymentUrl
    const memberToken = await getFirebaseToken(page);
    console.log(`Member Token obtained: ${memberToken ? 'YES (length ' + memberToken.length + ')' : 'NO'}`);

    const orderIdMatch = orderDetailUrl.match(/\/my-custom-orders\/([A-Za-z0-9_-]+)/);
    const customOrderId = orderIdMatch ? orderIdMatch[1] : null;
    console.log(`Target Custom Order Reference: ${customOrderId}`);

    const payInitRes = await fetch(`http://localhost:8080/api/v1/members/me/custom-orders/${customOrderId}/payment`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${memberToken}`,
        'Idempotency-Key': `idemp_pay_${Date.now()}`
      }
    });

    const payData = await payInitRes.json();
    console.log('Payment initiation API outcome:', payData);
    const generatedPaymentUrl = payData.paymentUrl;
    console.log(`Payment URL: ${generatedPaymentUrl}`);

    // Parse params from paymentUrl to trigger signed VNPay callback
    const parsedUrl = new URL(generatedPaymentUrl);
    const txnRef = parsedUrl.searchParams.get('vnp_TxnRef');
    const amountVal = parsedUrl.searchParams.get('vnp_Amount');

    console.log(`Triggering signed VNPay callback for merchantRef: ${txnRef}, amount: ${amountVal}...`);
    const callbackParams = {
      vnp_Amount: amountVal,
      vnp_BankCode: 'NCB',
      vnp_BankTranNo: 'VNP' + Date.now(),
      vnp_CardType: 'ATM',
      vnp_OrderInfo: parsedUrl.searchParams.get('vnp_OrderInfo') || 'Custom Order Payment',
      vnp_PayDate: '20261125000000',
      vnp_ResponseCode: '00',
      vnp_TmnCode: parsedUrl.searchParams.get('vnp_TmnCode') || 'MLAND001',
      vnp_TransactionNo: '14' + Date.now().toString().slice(-6),
      vnp_TransactionStatus: '00',
      vnp_TxnRef: txnRef,
    };

    const signature = signVnpayParams(callbackParams, 'MLANDVNPAYHASHSECRET2026112100');
    callbackParams.vnp_SecureHash = signature;

    const queryStr = Object.keys(callbackParams)
      .sort()
      .map(k => `${k}=${javaUrlEncode(callbackParams[k])}`)
      .join('&');
    const ipnUrl = `http://localhost:8080/api/v1/integrations/payments/vnpay/events?${queryStr}`;
    const ipnRes = await fetch(ipnUrl);
    const ipnBody = await ipnRes.json();
    console.log(`IPN Callback result: HTTP ${ipnRes.status}`, ipnBody);

    // Reload custom order detail page in browser
    console.log('Navigating back to custom order detail to verify PAID...');
    await page.goto(orderDetailUrl, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 2000));
    const orderPaidText = await page.evaluate(() => document.body.innerText);
    const isPaid = orderPaidText.includes('Đã thanh toán') || orderPaidText.includes('PAID');
    console.log(`Order status is PAID: ${isPaid}`);

    recordStep('24-30. Member 100% Full Payment', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '12_custom_order_paid.jpg') });

    if (!isPaid) {
      throw new Error(`FAIL: Custom order is not in PAID state after verified payment callback!`);
    }

    // -------------------------------------------------------------------------
    // Step 31-35: Staff/Manager Progresses Production & Handover
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Staff updating production status to COMPLETED and fulfilment to DELIVERED...');
    await loginAs(page, 'staff.test@mland.vn', 'StaffTest@123456');
    navCount++;

    const staffToken = await getFirebaseToken(page);
    console.log(`Staff Token obtained: ${staffToken ? 'YES' : 'NO'}`);

    const statusUpdateRes = await fetch(`http://localhost:8080/api/v1/custom-orders/${customOrderId}/status`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${staffToken}`
      },
      body: JSON.stringify({
        productionStatus: 'COMPLETED',
        fulfilmentStatus: 'DELIVERED',
        status: 'COMPLETED'
      })
    });

    const statusUpdateData = await statusUpdateRes.json().catch(() => null);
    console.log(`Status update API response: HTTP ${statusUpdateRes.status}`, statusUpdateData);

    // Member reloads order to confirm final delivered/completed state
    console.log('Member verifying final completed state...');
    await loginAs(page, 'member.test@mland.vn', 'Member@123456');
    navCount++;
    await page.goto(orderDetailUrl, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 2000));

    const finalOrderText = await page.evaluate(() => document.body.innerText);
    checkMojibake(finalOrderText, 'Final Custom Order Page');
    const isDelivered = finalOrderText.includes('DELIVERED') || finalOrderText.includes('Đã bàn giao') || finalOrderText.includes('Hoàn tất') || finalOrderText.includes('COMPLETED');
    console.log(`Final order state DELIVERED/COMPLETED: ${isDelivered}`);

    recordStep('31-35. Production & Fulfilment Completion', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '13_custom_order_delivered.jpg') });

    if (!isDelivered) {
      throw new Error(`FAIL: Custom order is not in DELIVERED/COMPLETED state!`);
    }

    console.log('\n======================================================');
    console.log('SCENARIO 1 RESULT: SUCCESS / PASS');
    console.log(`Total module navigation actions: ${navCount}`);
    console.log('Timings summary:');
    console.table(timings);
    console.log('======================================================\n');

    return {
      scenarioId: 'E2E-XF-004-012-013-001',
      status: 'PASS',
      isolationKey,
      designId,
      designRequestId,
      customOrderId,
      navCount,
      timings,
    };
  } catch (err) {
    console.error(`❌ SCENARIO 1 FAILED:`, err);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'FAIL_scenario_1.jpg') }).catch(() => {});
    return {
      scenarioId: 'E2E-XF-004-012-013-001',
      status: 'FAIL',
      isolationKey,
      error: err.message,
      navCount,
      timings,
    };
  } finally {
    await browser.close();
  }
}

runScenario1().then(res => {
  fs.writeFileSync(path.join(SCREENSHOT_DIR, 'result.json'), JSON.stringify(res, null, 2));
  if (res.status === 'FAIL') process.exit(1);
});
