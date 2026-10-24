// Optional verification only. Runtime prototype has zero package dependencies.
// PLAYWRIGHT_MODULE points to an installed playwright package's index.mjs.
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {scenarios,validateProfile,eligibleMember} from './model.js';
import {copy} from './copy.js';

const checks=[];
function check(name,fn){fn();checks.push(name);}
check('Unicode normalization, code points, controls, international phone',()=>{
  assert.deepEqual(validateProfile('\u2003Đặng  Ánh 🌷\u2003','').value,{fullName:'Đặng  Ánh 🌷',phone:null});
  assert.equal(Object.keys(validateProfile('🌷'.repeat(100),'+12').errors).length,0);
  for(const n of ['',' '.repeat(2),'🌷'.repeat(101),'An\u0000Linh','An\u0085Linh'])assert.ok(validateProfile(n,'').errors.fullName);
  for(const p of ['0912345678','+012','+1','+1234567890123456',' +84912345678','+84 912345678','+84912345678\n'])assert.ok(validateProfile('Linh',p).errors.phone);
  assert.equal(validateProfile('Linh','\u2003').value.phone,null);
});
check('Every VI key has matching EN copy',()=>assert.deepEqual(Object.keys(copy.vi).sort(),Object.keys(copy.en).sort()));
check('003 status allowlist does not depend on policy',()=>{
  assert.equal(eligibleMember('ACTIVE'),true);assert.equal(eligibleMember('PENDING_POLICY_ACCEPTANCE'),true);
  assert.equal(eligibleMember('SUSPENDED'),false);assert.equal(eligibleMember('UNKNOWN'),false);
  assert.ok(!scenarios.some(([id])=>id.startsWith('policy')));
});
if(!process.env.PLAYWRIGHT_MODULE){console.log(JSON.stringify({passed:checks,browser:'not run; set PLAYWRIGHT_MODULE'},null,2));process.exit(0);}
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1100}});
const page=await context.newPage(),network=[],errors=[];
page.on('pageerror',e=>errors.push(e.message));
page.on('request',r=>{if(!r.url().startsWith('http://127.0.0.1:8000/')&&!r.url().startsWith('data:'))network.push(r.url());});
const base='http://127.0.0.1:8000/003-member-profile/';
const open=async(scene,lang='vi')=>{await page.goto(`${base}?scene=${scene}&lang=${lang}`);await page.locator('h1').waitFor();};
const review=async id=>{await page.locator('.reviewer summary').click();await page.locator('#scenario').selectOption(id);await page.locator('#apply-scene').click();};
try{
  let layouts=0;
  for(const width of [320,390,768,1280,1440]){
    await page.setViewportSize({width,height:1100});
    for(const lang of ['vi','en'])for(const [scene] of scenarios){
      await open(scene,lang);
      const shape=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,heading:document.querySelector('h1').textContent,missingLabels:[...document.querySelectorAll('input')].filter(n=>!n.labels?.length).map(n=>n.id)}));
      assert.equal(shape.overflow,false,`${scene}/${lang}/${width}: horizontal overflow`);
      assert.ok(shape.heading.trim(),`${scene}: missing heading`);
      assert.deepEqual(shape.missingLabels,[],`${scene}: missing labels`);
      layouts++;
    }
  }
  checks.push(`${layouts} scene/language/viewport combinations without horizontal overflow or unlabeled input`);
  await page.setViewportSize({width:1440,height:1100});await open('profile-empty');
  await page.locator('#profile-form button').click();await page.locator('#fullName-error').waitFor();
  assert.equal(await page.locator('#fullName').evaluate(n=>n===document.activeElement),true);
  await page.locator('#fullName').fill('  Đặng  Ánh 🌷  ');await page.locator('#phone').fill('+84912345678');
  await page.locator('#profile-form button').click();await page.locator('.feedback.success').waitFor();
  assert.equal(await page.locator('#fullName').inputValue(),'Đặng  Ánh 🌷');
  assert.equal(await page.locator('#announcer').textContent(),copy.vi.saved);
  await page.locator('#phone').fill('');assert.equal(await page.locator('.feedback.success').count(),0);await page.locator('#profile-form button').click();await page.locator('.feedback.success').waitFor();
  assert.equal(await page.locator('#phone').inputValue(),'');
  checks.push('First save, error focus, Unicode preservation, normalization, optional phone clearing');
  await open('profile-failure');await page.locator('#profile-form button').click();await page.locator('.feedback.error').waitFor();
  assert.equal(await page.locator('#fullName').inputValue(),'Nguyễn An Linh Mai');
  await page.locator('#profile-form button').click();await page.locator('.feedback.success').waitFor();
  checks.push('Failed save retains draft, retry succeeds');
  await page.locator('#fullName').fill('<img src=x onerror=alert(1)>');await page.locator('#profile-form button').click();await page.locator('.feedback.success').waitFor();
  assert.equal(await page.locator('img').count(),0);
  await page.locator('[data-action=language]').click();assert.equal(await page.locator('#fullName').inputValue(),'<img src=x onerror=alert(1)>');
  checks.push('Escaped user content and draft preservation on language switch');
  const signIn=async()=>{await page.locator('#password').fill('dummy-only');await page.locator('#auth-form button').click();};
  const home=()=>page.getByRole('heading',{name:copy.vi.homeTitle}).waitFor();
  await open('signin');await signIn();await home();
  assert.equal(await page.locator('[data-action=resend]').count(),0);
  assert.equal(await page.locator('#profile-form').count(),0);
  await page.locator('[data-action=open-profile]').click();assert.equal(await page.locator('#fullName').inputValue(),'Nguyễn An Linh');
  checks.push('AC-02: verified sign-in goes to home; profile opens only by explicit action');
  await open('signup');await signIn();await page.getByRole('heading',{name:copy.vi.verifyTitle}).waitFor();
  await page.locator('#main').getByText(copy.vi.registrationEmailSent,{exact:true}).waitFor();
  await page.locator('[data-action=recheck]').click();await page.locator('#main').getByText(copy.vi.verifyPending,{exact:true}).waitFor();
  await page.locator('[data-action=resend]').click();await page.locator('#main').getByText(copy.vi.verifySent,{exact:true}).waitFor();
  await review('verified');await home();
  await page.locator('[data-action=open-profile]').click();
  assert.equal(await page.locator('#fullName').inputValue(),'');
  checks.push('AC-01/05: registration sends initial email, verification completes to home, first profile stays empty');
  await open('signin-unverified');await signIn();await page.getByRole('heading',{name:copy.vi.verifyTitle}).waitFor();
  assert.equal(await page.locator('.feedback').count(),0);
  assert.equal(await page.locator('#reviewer-root').getAttribute('data-resend-count'),'0');
  await page.locator('[data-action=recheck]').click();assert.equal(await page.locator('#reviewer-root').getAttribute('data-resend-count'),'0');
  await page.locator('[data-action=resend]').click();assert.equal(await page.locator('#reviewer-root').getAttribute('data-resend-count'),'1');
  checks.push('AC-03: unfinished registration resumes verification; entry/recheck never resend automatically');
  await open('profile-pending-policy');await page.locator('#fullName').fill('Member chờ policy');await page.locator('#profile-form button').click();await page.locator('.feedback.success').waitFor();
  assert.equal(await page.locator('#policy-form').count(),0);
  await page.locator('[data-action=logout]').click();await signIn();await home();await page.locator('[data-action=open-profile]').click();
  assert.equal(await page.locator('#fullName').inputValue(),'Member chờ policy');
  assert.ok((await page.locator('#reviewer-root').textContent()).includes('PENDING_POLICY_ACCEPTANCE'));
  checks.push('AC-04: pending-policy Member can sign in/read/save without consent screen or status conversion');
  await open('credential-stale');await page.locator('[data-action=signin]').click();await signIn();await home();
  assert.equal(await page.locator('[data-action=resend]').count(),0);
  checks.push('AC-07: stale credential recovers via fresh sign-in, without repeating email verification');
  for(const scene of ['expired','revoked','suspended','initialize','unavailable','credential-stale']){await open(scene);assert.equal(await page.locator('#profile-form').count(),0);await page.locator('.brand').click();assert.equal(await page.locator('#profile-form').count(),0);}
  await open('suspended');await page.locator('[data-action=signin]').click();await signIn();await page.getByRole('heading',{name:copy.vi.suspendedTitle}).waitFor();
  await open('initialize');await page.locator('[data-action=initialize-member]').click();await home();
  checks.push('AC-06: denied states stay closed; suspended sign-in never unlocks; Member bootstrap returns home');
  await open('profile');await page.keyboard.press('Tab');await page.keyboard.press('Enter');assert.equal(await page.evaluate(()=>location.hash),'#main');
  await page.locator('#fullName').focus();assert.ok(await page.locator('#fullName').evaluate(n=>getComputedStyle(n).outlineStyle!=='none'));
  await page.locator('.reviewer summary').click();await page.locator('#reset-demo').click();assert.equal(await page.locator('#fullName').inputValue(),'Nguyễn An Linh');
  assert.equal(await page.evaluate(()=>localStorage.length+sessionStorage.length),0);assert.deepEqual(await context.cookies(),[]);assert.deepEqual(network,[]);assert.deepEqual(errors,[]);
  checks.push('Keyboard skip/focus, reviewer reset, no storage/cookies/external network or JS errors');
  await mkdir('local-notes/prototype-003',{recursive:true});
  for(const [scene,width] of [['profile',1440],['profile',390],['signin',1440],['home',390],['credential-stale',390],['profile-invalid',320]]){await page.setViewportSize({width,height:1100});await open(scene);await page.screenshot({path:`local-notes/prototype-003/${scene}-${width}.png`,fullPage:true});}
  console.log(JSON.stringify({passed:checks,scenarios:scenarios.length,visualEvidence:'local-notes/prototype-003/*.png'},null,2));
}finally{await browser.close();}
