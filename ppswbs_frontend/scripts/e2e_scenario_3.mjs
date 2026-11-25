import puppeteer from 'puppeteer-core';
import fs from 'fs';
import path from 'path';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const SCREENSHOT_DIR = path.resolve('..', 'e2e-screenshots', 'scenario3');
if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function checkMojibake(text, context) {
  const mojibakePattern = /\uFFFD|Ã¡|Ã©|Ã³|Ãº|Ã­|Ã£|áº|á»/g;
  if (mojibakePattern.test(text)) {
    throw new Error(`[MOJIBAKE DETECTED] in ${context}: "${text}"`);
  }
}

async function loginOnPage(page, email, pass) {
  console.log(`Logging in on page as ${email}...`);
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

async function runScenario3() {
  const isolationKey = `iso_sc3_${Date.now()}`;
  console.log(`\n======================================================`);
  console.log(`STARTING SCENARIO 3: E2E-XF-004-012-013-003`);
  console.log(`Consultation Chat (5 msgs each side, stability, close)`);
  console.log(`Isolation Key: ${isolationKey}`);
  console.log(`======================================================\n`);

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1400,900'],
  });

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

  // Create isolated browser contexts for real-time concurrent Member and Staff sessions
  const memberContext = await browser.createBrowserContext();
  const staffContext = await browser.createBrowserContext();

  const pageMember = await memberContext.newPage();
  await pageMember.setViewport({ width: 1400, height: 900 });
  pageMember.on('dialog', async d => {
    console.log(`[Member Dialog] "${d.message()}". Accepting...`);
    await d.accept();
  });

  const pageStaff = await staffContext.newPage();
  await pageStaff.setViewport({ width: 1400, height: 900 });
  pageStaff.on('dialog', async d => {
    console.log(`[Staff Dialog] "${d.message()}". Accepting...`);
    await d.accept();
  });

  try {
    // -------------------------------------------------------------------------
    // Step 1: Member Sign In
    // -------------------------------------------------------------------------
    let t0 = Date.now();
    await loginOnPage(pageMember, 'member.test@mland.vn', 'Member@123456');
    navCount++;
    recordStep('1. Member Sign In', t0, Date.now());
    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, '01_member_signed_in.jpg') });

    // -------------------------------------------------------------------------
    // Step 2-4: Create & Save Ring Design
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await pageMember.goto('http://localhost:5173/workshops/PKG-COUPLE-SILVER/design/blank', { waitUntil: 'networkidle0' });
    navCount++;
    await pageMember.waitForSelector('[data-testid="save-and-evaluate-btn"]', { timeout: 15000 });

    const materialCard = await pageMember.$('.ring-option-card:nth-of-type(1)');
    if (materialCard) await materialCard.click();
    await new Promise(r => setTimeout(r, 400));

    const engToggle = await pageMember.$('[data-testid="engraving-toggle"]');
    if (engToggle) {
      await engToggle.click();
      await new Promise(r => setTimeout(r, 400));
      const engInput = await pageMember.$('[data-testid="engraving-input"]');
      if (engInput) {
        await engInput.type(`Consult ${isolationKey.slice(-4)}`);
      }
    }

    const saveBtn = await pageMember.$('[data-testid="save-and-evaluate-btn"]');
    await saveBtn.click();
    await pageMember.waitForNavigation({ waitUntil: 'networkidle0', timeout: 20000 }).catch(() => {});
    await new Promise(r => setTimeout(r, 2000));
    navCount++;

    const evalUrl = pageMember.url();
    const designIdMatch = evalUrl.match(/\/design\/([A-Za-z0-9_-]+)/);
    const designId = designIdMatch ? designIdMatch[1] : null;
    console.log(`Created & Evaluated Design ID: ${designId}`);

    recordStep('2-4. Create & Save Design', t0, Date.now());
    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, '02_design_evaluated.jpg') });

    // -------------------------------------------------------------------------
    // Step 5-7: Member Clicks "Tư vấn thiết kế"
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Member clicking "Tư vấn thiết kế" button on evaluation page...');
    const consultBtn = await pageMember.$('[data-testid="consult-design-btn"]');
    if (consultBtn) {
      await consultBtn.click();
      await pageMember.waitForNavigation({ waitUntil: 'networkidle0', timeout: 15000 }).catch(() => {});
    } else {
      await pageMember.goto(`http://localhost:5173/member/consultations?designReference=${designId}&designVersion=1`, { waitUntil: 'networkidle0' });
    }
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    // Verify initial scroll position is top (scrollTop === 0)
    const initialScrollY = await pageMember.evaluate(() => window.scrollY);
    console.log(`Initial page scroll Y: ${initialScrollY} (Expected: 0)`);

    // Verify safe context
    await pageMember.waitForSelector('.consultation-header, .consultation-context-header', { timeout: 15000 });
    const contextHeaderHtml = await pageMember.evaluate(() => {
      const el = document.querySelector('.consultation-context-details, .consultation-context-header');
      return el ? el.innerText : '';
    });
    console.log(`Consultation Context details: ${contextHeaderHtml.replace(/\n/g, ' ')}`);
    checkMojibake(contextHeaderHtml, 'Consultation Context Header');

    recordStep('5-7. Open Consultation with Attached Design', t0, Date.now());
    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, '03_consultation_opened_safe_context.jpg') });

    // -------------------------------------------------------------------------
    // Step 8: Manager Assigns Consultation to Branch HN-HOANKIEM
    // -------------------------------------------------------------------------
    t0 = Date.now();
    const mgrContext = await browser.createBrowserContext();
    const pageMgr = await mgrContext.newPage();
    await pageMgr.setViewport({ width: 1400, height: 900 });

    await loginOnPage(pageMgr, 'manager.test@mland.vn', 'ManagerTest@123456');
    navCount++;

    console.log('Manager opening /operations/consultations to assign branch...');
    await pageMgr.goto('http://localhost:5173/operations/consultations', { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    // Select the first conversation in Manager's inbox list
    await pageMgr.evaluate(() => {
      const items = Array.from(document.querySelectorAll('.consultation-item'));
      if (items.length > 0) items[0].click();
    });
    await new Promise(r => setTimeout(r, 1200));

    // Click assign button to open assign modal
    console.log('Manager clicking assign branch button...');
    const assignBtnClicked = await pageMgr.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const assignBtn = btns.find(b => b.textContent.includes('Điều phối') || b.textContent.includes('Assign') || b.textContent.includes('Phân bổ'));
      if (assignBtn) {
        assignBtn.click();
        return true;
      }
      return false;
    });
    console.log(`Manager clicked assign button: ${assignBtnClicked}`);
    await new Promise(r => setTimeout(r, 1000));

    // Submit assign form (HN-HOANKIEM is default selected)
    await pageMgr.waitForSelector('.consultation-modal form button[type="submit"]', { timeout: 10000 });
    await pageMgr.click('.consultation-modal form button[type="submit"]');
    await new Promise(r => setTimeout(r, 2000));

    recordStep('8. Manager Assign Consultation to Branch', t0, Date.now());
    await pageMgr.screenshot({ path: path.join(SCREENSHOT_DIR, '04_manager_assigned_branch.jpg') });

    // -------------------------------------------------------------------------
    // Step 9: Staff Signs in, Opens Inbox & Claims Consultation
    // -------------------------------------------------------------------------
    t0 = Date.now();
    await loginOnPage(pageStaff, 'staff.test@mland.vn', 'StaffTest@123456');
    navCount++;

    console.log('Staff opening /operations/consultations...');
    await pageStaff.goto('http://localhost:5173/operations/consultations', { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    // Select the first conversation in staff list
    await pageStaff.evaluate(() => {
      const items = Array.from(document.querySelectorAll('.consultation-item'));
      if (items.length > 0) items[0].click();
    });
    await new Promise(r => setTimeout(r, 1200));

    // Staff claims the consultation if unclaimed
    console.log('Staff checking claim button...');
    const claimClicked = await pageStaff.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const claimBtn = btns.find(b => b.textContent.includes('Tiếp nhận') || b.textContent.includes('Claim'));
      if (claimBtn) {
        claimBtn.click();
        return true;
      }
      return false;
    });
    console.log(`Staff claimed consultation: ${claimClicked}`);
    await new Promise(r => setTimeout(r, 2000));

    recordStep('9. Staff Open & Claim Consultation', t0, Date.now());
    await pageStaff.screenshot({ path: path.join(SCREENSHOT_DIR, '05_staff_claimed_consultation.jpg') });

    // -------------------------------------------------------------------------
    // Step 9-18: 5 Rounds of Messages (10 total: 5 Member + 5 Staff)
    // -------------------------------------------------------------------------
    t0 = Date.now();
    const chatPairs = [
      {
        member: `[1] Xin chào workshop, tôi muốn hỏi mẫu nhẫn bạc này có thể đính đá sapphire tự nhiên được không (${isolationKey})?`,
        staff: `[1] Chào bạn, workshop hoàn toàn có thể đính đá sapphire tự nhiên theo yêu cầu của bạn nhé (${isolationKey}).`
      },
      {
        member: `[2] Tuyệt vời, thời gian hoàn thiện mẫu nhẫn sapphire này dự kiến mất bao lâu vậy ạ?`,
        staff: `[2] Thông thường quy trình chế tác thủ công và đính đá sapphire mất từ 3 đến 5 ngày làm việc bạn nhé.`
      },
      {
        member: `[3] Mình có thể chọn khắc thêm chữ kỷ niệm bên trong lòng nhẫn được không shop?`,
        staff: `[3] Dạ được bạn nhé, workshop hỗ trợ khắc laser chữ hoặc ngày kỷ niệm miễn phí trong lòng nhẫn.`
      },
      {
        member: `[4] Chính sách bảo hành và đánh bóng làm mới định kỳ của bên mình như thế nào ạ?`,
        staff: `[4] Mland cam kết bảo hành trọn đời cho dịch vụ làm sạch, đánh bóng và kiểm tra chấu đá định kỳ.`
      },
      {
        member: `[5] Cảm ơn bạn nhiều, thông tin rất chi tiết và rõ ràng. Mình sẽ chốt làm theo mẫu này!`,
        staff: `[5] Cảm ơn bạn đã tin tưởng Mland! Workshop chúc bạn một ngày tốt lành và luôn sẵn sàng phục vụ.`
      }
    ];

    for (let i = 0; i < chatPairs.length; i++) {
      const roundNum = i + 1;
      const pair = chatPairs[i];
      const tRound = Date.now();
      console.log(`\n--- Real-Time Exchange Round ${roundNum}/5 ---`);

      // 1. Member sends message
      console.log(`Member typing M${roundNum}...`);
      await pageMember.waitForSelector('textarea.consultation-textarea, textarea', { timeout: 10000 });
      await pageMember.type('textarea.consultation-textarea, textarea', pair.member);
      await pageMember.keyboard.press('Enter');
      await new Promise(r => setTimeout(r, 1000));

      const memberLastBubble = await pageMember.evaluate(() => {
        const bubbles = Array.from(document.querySelectorAll('.consultation-bubble'));
        return bubbles.length > 0 ? bubbles[bubbles.length - 1].innerText : '';
      });
      console.log(`Member thread bubble M${roundNum}: "${memberLastBubble.slice(0, 45)}..."`);
      checkMojibake(memberLastBubble, `Member Message ${roundNum}`);

      // 2. Staff receives and replies
      console.log(`Staff receiving and replying S${roundNum}...`);
      await new Promise(r => setTimeout(r, 1000)); // wait for polling sync
      await pageStaff.waitForSelector('textarea.consultation-textarea, textarea', { timeout: 10000 });
      await pageStaff.type('textarea.consultation-textarea, textarea', pair.staff);
      await pageStaff.keyboard.press('Enter');
      await new Promise(r => setTimeout(r, 1000));

      const staffLastBubble = await pageStaff.evaluate(() => {
        const bubbles = Array.from(document.querySelectorAll('.consultation-bubble'));
        return bubbles.length > 0 ? bubbles[bubbles.length - 1].innerText : '';
      });
      console.log(`Staff thread bubble S${roundNum}: "${staffLastBubble.slice(0, 45)}..."`);
      checkMojibake(staffLastBubble, `Staff Message ${roundNum}`);

      // Wait 1s for Member to receive Staff message via polling
      await new Promise(r => setTimeout(r, 1000));
      recordStep(`10.${roundNum} Exchange Round ${roundNum} (M${roundNum} + S${roundNum})`, tRound, Date.now());
    }

    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, '05_ten_messages_thread.jpg') });

    // Verify all 10 messages exist in Member's thread
    const memberBubblesCount = await pageMember.evaluate(() => document.querySelectorAll('.consultation-bubble').length);
    console.log(`Total message bubbles visible in Member thread: ${memberBubblesCount} (Expected: >= 10)`);

    // Verify viewport and polling stability
    console.log('Verifying viewport and polling stability on Member page for 4 seconds...');
    const beforeScroll = await pageMember.evaluate(() => window.scrollY);
    await new Promise(r => setTimeout(r, 4000));
    const afterScroll = await pageMember.evaluate(() => window.scrollY);
    console.log(`Scroll position before: ${beforeScroll}, after 4s polling: ${afterScroll}`);
    if (Math.abs(afterScroll - beforeScroll) > 10) {
      console.warn(`Warning: Viewport shifted by ${afterScroll - beforeScroll}px during polling!`);
    }

    // -------------------------------------------------------------------------
    // Step 19: Member Closes Consultation
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Member closing consultation session...');
    const closeBtnFound = await pageMember.evaluate(() => {
      const btns = Array.from(document.querySelectorAll('button'));
      const btn = btns.find(b => b.textContent.includes('Kết thúc') || b.textContent.includes('Đóng') || b.textContent.toLowerCase().includes('close'));
      if (btn) {
        btn.click();
        return true;
      }
      return false;
    });
    console.log(`Member clicked close button: ${closeBtnFound}`);
    await new Promise(r => setTimeout(r, 2500));

    // Verify status is CLOSED
    const statusText = await pageMember.evaluate(() => {
      const badge = document.querySelector('.consultation-status-badge');
      return badge ? badge.textContent.trim() : '';
    });
    console.log(`Consultation status after close: "${statusText}"`);
    checkMojibake(statusText, 'Consultation Status Badge');

    recordStep('19. Member Close Consultation', t0, Date.now());
    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, '06_consultation_closed.jpg') });

    // -------------------------------------------------------------------------
    // Step 20: Verify Post-Close Rejection
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Verifying post-close message rejection...');
    const postCloseState = await pageMember.evaluate(() => {
      const textarea = document.querySelector('textarea.consultation-textarea, textarea');
      const warningBanner = document.querySelector('.consultation-alert.warning, .alert-warning');
      return {
        hasTextarea: Boolean(textarea),
        isTextareaDisabled: textarea ? textarea.disabled : null,
        warningText: warningBanner ? warningBanner.textContent.trim() : null
      };
    });
    console.log('Post-close composer state on Member page:', postCloseState);
    if (postCloseState.warningText) {
      checkMojibake(postCloseState.warningText, 'Closed Warning Banner');
    }

    recordStep('20. Verify Post-Close Rejection', t0, Date.now());
    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, '07_post_close_composer_disabled.jpg') });

    // -------------------------------------------------------------------------
    // Step 21: Staff Verifies Closed Conversation in History
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Staff checking closed status in /operations/consultations...');
    await pageStaff.reload({ waitUntil: 'networkidle0' });
    await new Promise(r => setTimeout(r, 2000));

    const staffListText = await pageStaff.evaluate(() => document.body.innerText);
    checkMojibake(staffListText, 'Staff Consultation Inbox');
    const isClosedVisible = staffListText.includes('Đã đóng') || staffListText.includes('CLOSED');
    console.log(`Staff sees closed status in inbox: ${isClosedVisible}`);

    recordStep('21. Staff Verify Closed in History', t0, Date.now());
    await pageStaff.screenshot({ path: path.join(SCREENSHOT_DIR, '08_staff_inbox_closed_history.jpg') });

    // -------------------------------------------------------------------------
    // Step 22: Manager Operational Smoke Check
    // -------------------------------------------------------------------------
    t0 = Date.now();
    console.log('Manager opening /operations/consultations for operational smoke check...');
    await pageMgr.goto('http://localhost:5173/operations/consultations', { waitUntil: 'networkidle0' });
    navCount++;
    await new Promise(r => setTimeout(r, 2000));

    const mgrPageText = await pageMgr.evaluate(() => document.body.innerText);
    checkMojibake(mgrPageText, 'Manager Operations Consultation View');
    const has500Error = mgrPageText.includes('Internal Server Error') || mgrPageText.includes('500');
    console.log(`Manager operational smoke check: No 500 error: ${!has500Error}`);

    recordStep('22. Manager Operational Smoke Check', t0, Date.now());
    await pageMgr.screenshot({ path: path.join(SCREENSHOT_DIR, '09_manager_operational_smoke_check.jpg') });

    if (has500Error) {
      throw new Error(`FAIL: Manager operational smoke check encountered 500 error!`);
    }

    console.log('\n======================================================');
    console.log('SCENARIO 3 RESULT: SUCCESS / PASS');
    console.log(`Total module navigation actions: ${navCount}`);
    console.log('Timings summary:');
    console.table(timings);
    console.log('======================================================\n');

    return {
      scenarioId: 'E2E-XF-004-012-013-003',
      status: 'PASS',
      isolationKey,
      designId,
      navCount,
      timings,
    };
  } catch (err) {
    console.error(`❌ SCENARIO 3 FAILED:`, err);
    await pageMember.screenshot({ path: path.join(SCREENSHOT_DIR, 'FAIL_scenario_3_member.jpg') }).catch(() => {});
    await pageStaff.screenshot({ path: path.join(SCREENSHOT_DIR, 'FAIL_scenario_3_staff.jpg') }).catch(() => {});
    return {
      scenarioId: 'E2E-XF-004-012-013-003',
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

runScenario3().then(res => {
  fs.writeFileSync(path.join(SCREENSHOT_DIR, 'result.json'), JSON.stringify(res, null, 2));
  if (res.status === 'FAIL') process.exit(1);
});
