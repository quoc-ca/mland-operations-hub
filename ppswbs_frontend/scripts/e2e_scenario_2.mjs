import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots', 'scenario2');
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

async function runScenario2() {
  const isolationKey = `iso_sc2_${Date.now()}`;
  console.log(`\n======================================================`);
  console.log(`STARTING SCENARIO 2: E2E-XF-004-012-013-002`);
  console.log(`Clarification & Review Again Flow`);
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
    // Step 1: Member Sign In
    // -------------------------------------------------------------------------
    let t0 = Date.now();
    await loginAs(page, 'member.test@mland.vn', 'Member@123456');
    navCount++;
    recordStep('1. Member Sign In', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '01_member_signed_in.jpg') });

    // -------------------------------------------------------------------------
    // Step 2-4: Ring Configurator & Dynamic Preview
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await page.goto('http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/blank', { waitUntil: 'networkidle0' });
    navCount++;
    await page.waitForSelector('[data-testid="save-and-evaluate-btn"]', { timeout: 15000 });

    const materialCard = await page.$('.ring-option-card:nth-of-type(1)');
    if (materialCard) await materialCard.click();
    await new Promise(r => setTimeout(r, 500));

    // Enable Engraving text
    const engToggle = await page.$('[data-testid="engraving-toggle"]');
    if (engToggle) {
      await engToggle.click();
      await new Promise(r => setTimeout(r, 500));
      const engInput = await page.$('[data-testid="engraving-input"]');
      if (engInput) {
        await engInput.type(`Clarify ${isolationKey.slice(-4)}`);
      }
    }

    recordStep('2-4. Ring Configurator & Dynamic Preview', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02_ring_configuration.jpg') });

    // -------------------------------------------------------------------------
    // Step 5-7: Save & Evaluate Design
    // -------------------------------------------------------------------------
    t0 = Date.now();
    const saveBtn = await page.$('[data-testid="save-and-evaluate-btn"]');
    await saveBtn.click();
    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 20000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 2000));
    navCount++;

    const currentUrl = page.url();
    const designIdMatch = currentUrl.match(/\/design\/([A-Za-z0-9_-]+)/);
    const designId = designIdMatch ? designIdMatch[1] : null;
    console.log(`Created & Saved Design ID: ${designId}`);

    recordStep('5-7. Save & Evaluate Design', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '03_design_evaluated.jpg') });

    // -------------------------------------------------------------------------
    // Step 8-11: Submit Custom Design Request (Initial: NEED_REVIEW)
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await page.goto(`http://localhost:5173/design-requests/new?designReference=${designId || ''}`, { waitUntil: 'networkidle0' });
    navCount++;
    await page.waitForSelector('textarea', { timeout: 15000 });

    const initialDesc = `Yêu cầu chế tác nhẫn bạc cần thẩm định chi tiết giác cắt đá cho ${isolationKey}.`;
    await page.type('textarea', initialDesc);

    const submitBtn = await page.$('button[type="submit"]');
    await submitBtn.click();
    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 15000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 2000));
    navCount++;

    const reqUrl = page.url();
    const reqMatch = reqUrl.match(/\/design-requests\/([A-Za-z0-9_-]+)/);
    const designRequestId = reqMatch ? reqMatch[1] : null;
    console.log(`Created Design Request ID: ${designRequestId}`);

    recordStep('8-11. Submit Custom Design Request', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04_request_created.jpg') });

    // -------------------------------------------------------------------------
    // Step 12-14: Staff Review: CLARIFICATION_REQUIRED with Explanation
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'staff.test@mland.vn', 'StaffTest@123456');
    navCount++;

    console.log(`Staff opening review detail: ${designRequestId}...`);
    await page.goto(`http://localhost:5173/design-review-requests/${designRequestId}`, { waitUntil: 'networkidle0' });
    navCount++;
    await page.waitForSelector('textarea', { timeout: 15000 });

    // Select CLARIFICATION_REQUIRED
    await page.evaluate(() => {
      const select = document.querySelector('select');
      if (select) {
        select.value = 'CLARIFICATION_REQUIRED';
        select.dispatchEvent(new Event('change', { bubbles: true }));
      }
    });

    const clarifyReason = `Vui lòng làm rõ kích thước viên đá chủ và bề dày thân nhẫn để workshop kiểm tra khuôn đúc (${isolationKey}).`;
    await page.type('textarea', clarifyReason);

    console.log('Staff submitting CLARIFICATION_REQUIRED decision...');
    const decisionSubmitBtn = await page.$('form button[type="submit"]');
    await decisionSubmitBtn.click();
    await new Promise(r => setTimeout(r, 600));

    // Confirm dialog
    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('12-14. Staff CLARIFICATION_REQUIRED Decision', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '05_staff_clarification_required.jpg') });

    // -------------------------------------------------------------------------
    // Step 15-18: Member Sees Explanation & Submits Clarification Response
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'member.test@mland.vn', 'Member@123456');
    navCount++;

    console.log('Member navigating to /account/my-custom-orders to view requests...');
    await page.goto('http://localhost:5173/account/my-custom-orders', { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 1500));

    // Click tab 'Yêu cầu chế tác'
    console.log('Switching to Requests tab...');
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const reqTab = btns.find(b => b.textContent.includes('Yêu cầu chế tác') || b.textContent.includes('Yêu cầu'));
      if (reqTab) reqTab.click();
    });
    await new Promise(r => setTimeout(r, 1500));
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '06_member_clarification_view.jpg') });

    // Verify clarification explanation is visible
    const requestsPageText = await page.evaluate(() => document.body.innerText);
    checkMojibake(requestsPageText, 'Requests List Page');
    const hasClarifyNotice = requestsPageText.includes('Cần làm rõ thêm') || requestsPageText.includes('CLARIFICATION_REQUIRED');
    console.log(`Requests list shows CLARIFICATION_REQUIRED tag: ${hasClarifyNotice}`);

    // Click 'Phản hồi làm rõ' button
    console.log('Clicking respond to clarification button...');
    const modalOpened = await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const clarifyBtn = btns.find(b => b.textContent.includes('Phản hồi làm rõ') || b.textContent.includes('Làm rõ'));
      if (clarifyBtn) {
        clarifyBtn.click();
        return true;
      }
      return false;
    });
    console.log(`Clarification modal opened: ${modalOpened}`);
    await new Promise(r => setTimeout(r, 1000));

    // Type clarification text and submit
    const memberClarifResponse = `Xác nhận đá chủ 4.5mm giác cắt tròn, thân nhẫn dày 1.8mm và rộng 3.5mm theo chuẩn workshop (${isolationKey}).`;
    await page.waitForSelector('form textarea', { timeout: 8000 });
    await page.type('form textarea', memberClarifResponse);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '07_member_clarification_modal_input.jpg') });

    console.log('Submitting clarification response...');
    await page.evaluate(() => {
      const submitBtn = document.querySelector('form button[type="submit"]');
      if (submitBtn) submitBtn.click();
    });
    await new Promise(r => setTimeout(r, 2500));

    recordStep('15-18. Member Submit Clarification Response', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '08_member_clarification_submitted.jpg') });

    // -------------------------------------------------------------------------
    // Step 19-21: Staff Reviews Revision & Accepts
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'staff.test@mland.vn', 'StaffTest@123456');
    navCount++;

    console.log(`Staff opening review detail after revision: ${designRequestId}...`);
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

    const acceptedExplanation = `Đã nhận đủ thông tin làm rõ từ khách hàng. Thiết kế bạc 925 đính đá 4.5mm hoàn toàn khả thi (${isolationKey}).`;
    await page.type('textarea', acceptedExplanation);

    const staffSubmitBtn = await page.$('form button[type="submit"]');
    await staffSubmitBtn.click();
    await new Promise(r => setTimeout(r, 600));

    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('19-21. Staff Accept Revised Request', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '09_staff_accepted_revision.jpg') });

    // -------------------------------------------------------------------------
    // Step 22: Staff Prepares Quote
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Staff preparing quote for accepted revised request...');
    await page.waitForSelector('input[data-testid="quote-amount-input"], input#quote-amount', { timeout: 10000 });

    await page.evaluate(() => {
      const amtInput = document.querySelector('input[data-testid="quote-amount-input"], input#quote-amount');
      const hrsInput = document.querySelector('input[data-testid="quote-hours-input"], input#quote-hours');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;

      if (amtInput) {
        setter.call(amtInput, '650000');
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

    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('22. Staff Prepare Quote', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '10_staff_quote_prepared.jpg') });

    // -------------------------------------------------------------------------
    // Step 23-25: Manager Approves Quote
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'manager.test@mland.vn', 'ManagerTest@123456');
    navCount++;

    console.log(`Manager opening review detail: ${designRequestId}...`);
    await page.goto(`http://localhost:5173/design-review-requests/${designRequestId}`, { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    console.log('Manager clicking approve quote...');
    await page.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const btn = btns.find(b => b.textContent.includes('Phê duyệt Báo giá') || b.textContent.includes('Phê duyệt báo giá') || b.textContent.toLowerCase().includes('phê duyệt'));
      if (btn) btn.click();
    });
    await new Promise(r => setTimeout(r, 800));

    await page.waitForSelector('.dialog-actions button.button.primary', { timeout: 8000 });
    await page.click('.dialog-actions button.button.primary');
    await new Promise(r => setTimeout(r, 2500));

    recordStep('23-25. Manager Approve Quote', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '11_manager_quote_approved.jpg') });

    // -------------------------------------------------------------------------
    // Step 26-30: Member Full Payment (100%)
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'member.test@mland.vn', 'Member@123456');
    navCount++;

    await page.goto('http://localhost:5173/account/my-custom-orders', { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    // Open first custom order
    await page.evaluate(() => {
      const links = Array.from(document.querySelectorAll('a[href*="/account/my-custom-orders/"]'));
      if (links.length > 0) links[0].click();
    });
    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 10000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 1500));
    navCount++;

    const orderDetailUrl = page.url();
    const orderIdMatch = orderDetailUrl.match(/\/my-custom-orders\/([A-Za-z0-9_-]+)/);
    const customOrderId = orderIdMatch ? orderIdMatch[1] : null;
    console.log(`Target Custom Order: ${customOrderId}`);

    const memberToken = await getFirebaseToken(page);
    console.log(`Member Token obtained: ${Boolean(memberToken)}`);

    const payInitRes = await fetch(`http://localhost:8080/api/v1/members/me/custom-orders/${customOrderId}/payment`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${memberToken}`,
        'Idempotency-Key': `idemp_pay_${Date.now()}`
      }
    });

    const payData = await payInitRes.json();
    const generatedPaymentUrl = payData.paymentUrl;
    console.log(`Generated payment URL: ${generatedPaymentUrl}`);

    const parsedUrl = new URL(generatedPaymentUrl);
    const txnRef = parsedUrl.searchParams.get('vnp_TxnRef');
    const amountVal = parsedUrl.searchParams.get('vnp_Amount');

    console.log(`Triggering verified VNPay callback for ${txnRef}, amount: ${amountVal}...`);
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

    // Reload order page to verify PAID
    await page.goto(orderDetailUrl, { waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 2000));
    const orderPaidText = await page.evaluate(() => document.body.innerText);
    const isPaid = orderPaidText.includes('Đã thanh toán') || orderPaidText.includes('PAID');
    console.log(`Custom order status is PAID: ${isPaid}`);

    recordStep('26-30. Member 100% Full Payment', t0, Date.now());
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, '12_custom_order_paid.jpg') });

    if (!isPaid) {
      throw new Error(`FAIL: Custom order is not in PAID state after verified payment callback!`);
    }

    // -------------------------------------------------------------------------
    // Step 31-35: Production & Fulfilment to DELIVERED
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginAs(page, 'staff.test@mland.vn', 'StaffTest@123456');
    navCount++;

    const staffToken = await getFirebaseToken(page);
    console.log(`Staff Token obtained: ${Boolean(staffToken)}`);

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
    console.log('SCENARIO 2 RESULT: SUCCESS / PASS');
    console.log(`Total module navigation actions: ${navCount}`);
    console.log('Timings summary:');
    console.table(timings);
    console.log('======================================================\n');

    return {
      scenarioId: 'E2E-XF-004-012-013-002',
      status: 'PASS',
      isolationKey,
      designId,
      designRequestId,
      customOrderId,
      navCount,
      timings,
    };
  } catch (err) {
    console.error(`❌ SCENARIO 2 FAILED:`, err);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'FAIL_scenario_2.jpg') }).catch(() => {});
    return {
      scenarioId: 'E2E-XF-004-012-013-002',
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

runScenario2().then(res => {
  fs.writeFileSync(path.join(SCREENSHOT_DIR, 'result.json'), JSON.stringify(res, null, 2));
  if (res.status === 'FAIL') process.exit(1);
});
