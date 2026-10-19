import {copy} from './copy.js';
import {fixture, scenarios, freshState, selectScenario, validateProfile, eligibleMember} from './model.js';
import {mountReviewer} from './reviewer.js';

const root=document.querySelector('#app');
const params=new URLSearchParams(location.search);
let state=selectScenario(scenarios.some(s=>s[0]===params.get('scene'))?params.get('scene'):'profile',params.get('lang')==='en'?'en':'vi');
let epoch=0;
const t=k=>copy[state.lang][k]||k;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=(name,cls='')=>`<svg class="icon ${cls}" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${({user:'<circle cx="12" cy="8" r="3.5"/><path d="M5 21v-3a7 7 0 0 1 14 0v3"/>',lock:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',check:'<path d="m5 12 4 4L19 6"/>',mail:'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 6 9 7 9-7"/>',exit:'<path d="M9 4H4v16h5m5-12 4 4-4 4m-6-4h10"/>',info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/>',globe:'<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a17 17 0 0 1 0 18 17 17 0 0 1 0-18"/>',shield:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8 12 3 3 5-6"/>'})[name]||''}</svg>`;
const action=(key,id,secondary=false,disabled=false)=>`<button type="button" class="button ${secondary?'secondary':''}" data-action="${id}" ${disabled?'disabled':''}>${t(key)}${!secondary?icon('arrow'):''}</button>`;
function banner(key,tone='info') {return key?`<div class="feedback ${tone}" ${tone==='error'?'role="alert"':''}>${icon(tone==='success'?'check':'info')}<span>${esc(t(key))}</span></div>`:'';}
function field(id,label,value,{hint='',error='',type='text',placeholder='',required=false,disabled=false}={}) {
  return `<div class="field"><label for="${id}">${t(label)}${required?` <span class="required">· ${t('required')}</span>`:''}</label><input id="${id}" name="${id}" type="${type}" value="${esc(value)}" placeholder="${esc(placeholder)}" ${required?'required':''} ${disabled?'disabled':''} autocomplete="off" ${id==='phone'?'inputmode="tel"':''} aria-invalid="${!!error}" aria-describedby="${id}-hint${error?` ${id}-error`:''}"><p class="hint" id="${id}-hint">${esc(t(hint))}</p>${error?`<p class="field-error" id="${id}-error">${esc(t(error))}</p>`:''}</div>`;
}
function profile() {
  const errors=Object.keys(state.errors).length;
  const stamp=state.saved?.updatedAt?new Intl.DateTimeFormat(state.lang==='vi'?'vi-VN':'en-GB',{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(state.saved.updatedAt))+' · GMT+7':t('neverUpdated');
  return `<div class="profile-layout"><aside class="account-nav"><p class="eyebrow">${t('account')}</p><div class="nav-current" aria-current="page">${icon('user')}${t('profileNav')}</div><p class="nav-caption">${icon('shield')}${t('securityNav')}</p><div class="nav-bottom"><span class="small-dot"></span>${t('active')}</div></aside><div class="profile-main"><header class="page-heading"><div class="eyebrow">${t('member')}</div><h1 tabindex="-1">${t('profileTitle')}</h1><p class="intro">${t('profileIntro')}</p></header><div class="profile-columns"><section class="card profile-card" aria-labelledby="personal-title"><div class="card-heading"><span class="section-number">01</span><div><h2 id="personal-title">${t('personal')}</h2><p class="muted">${t('personalNote')}</p></div></div>${banner(state.notice,state.notice==='saved'?'success':state.notice==='saveFailure'?'error':'info')}${errors?banner('fixErrors','error'):''}${!state.saved?banner('empty'):''}<div class="identity"><div class="field-label">${t('identity')} <span class="pill">${icon('check')}${t('verified')}</span></div><div class="identity-value">${icon('mail')}<span>${fixture.email}</span>${icon('lock')}</div><p class="hint">${t('readonly')}</p></div><form id="profile-form" novalidate aria-busy="${state.busy}">${field('fullName','name',state.draft.fullName,{required:true,hint:'nameHint',error:state.errors.fullName,disabled:state.busy})}${field('phone','phone',state.draft.phone,{type:'tel',hint:'phoneHint',error:state.errors.phone,placeholder:t('phonePlaceholder'),disabled:state.busy})}<div class="phone-note">${icon('info')}${t('unverified')} · ${t('optional')}</div><div class="save-row"><p class="updated">${t('updated')}<br><strong>${esc(stamp)}</strong></p><button class="button" type="submit" ${state.busy?'disabled':''}>${t(state.busy?'saving':'save')}${icon(state.busy?'lock':'arrow')}</button></div></form></section><aside class="privacy-note">${icon('shield','privacy-symbol')}<p class="eyebrow">MLAND / MEMBER CARE</p><h2>${t('privateTitle')}</h2><p>${t('privateBody')}</p><div class="note-divider"></div><p class="note-check">${icon('check')}${t('emailSafe')}</p><p class="note-check">${icon('check')}${t('phoneOptional')}</p><p class="note-foot">${t('privateFoot')}</p></aside></div><p class="security-line">${icon('lock')}${t('secure')}</p></div></div>`;
}
function auth() {
  const signup=state.scene==='signup';
  const note=state.scene==='auth-error'?'authError':state.scene==='auth-outage'?'authOutage':state.scene==='auth-loading'?'authLoading':state.notice;
  return `<div class="auth-layout"><aside class="auth-story"><p class="eyebrow">MLAND / MEMBER SPACE</p><h2>${t('authAside').replace('\n','<br>')}</h2><p>${t('authAsideBody')}</p><div class="ring-art" aria-hidden="true"><i></i><i></i><span>m.</span></div><p class="story-footer">${t('secure')}</p></aside><section class="auth-panel"><div class="eyebrow">${t('authStep')}</div><h1 tabindex="-1">${t(signup?'signup':'signin')}</h1><p class="intro">${t(signup?'signupIntro':'signinIntro')}</p>${banner(note,note==='authLoading'?'info':'error')}<form id="auth-form" novalidate aria-busy="${state.busy}">${field('email','email',fixture.email,{type:'email',required:true,hint:'demoEmailHint',disabled:state.busy})}${field('password','password','',{type:'password',required:true,hint:'passwordHint',disabled:state.busy})}<button class="button full-width" type="submit" ${state.busy?'disabled':''}>${t(state.busy?'authLoading':signup?'signupAction':'signinAction')}${icon('arrow')}</button></form><p class="auth-switch">${t(signup?'hasAccount':'needAccount')} <button class="text-button" data-action="${signup?'signin':'signup'}">${t(signup?'signinLink':'signupLink')}</button></p><p class="demo-disclaimer">${icon('info')}${t('authDisclaimer')}</p></section></div>`;
}
function verification() {
  const done=state.scene==='verified';
  return `<section class="journey-card"><div class="journey-icon">${icon(done?'check':'mail')}</div><p class="eyebrow">02 / ${state.lang==='vi'?'XÁC MINH EMAIL':'EMAIL VERIFICATION'}</p><h1 tabindex="-1">${t(done?'verifiedTitle':'verifyTitle')}</h1><p class="intro">${t(done?'verifiedIntro':'verifyIntro')}</p>${banner(state.notice|| (state.scene==='verify-sent'?'verifySent':''),'info')}<div class="email-slip"><span class="eyebrow">${t('verifyAddress')}</span><strong>${fixture.email}</strong></div><p class="muted">${t('verifyNote')}</p><div class="stack-actions">${done?action('returnHome','finish-auth'):action('recheck','recheck')+action('resend','resend',true)}<button class="text-button" data-action="signin">${t('switchAccount')}</button></div></section>`;
}
function homeDestination() {
  return `<section class="journey-card"><div class="journey-icon">${icon('check')}</div><p class="eyebrow">${t('homeDestination')}</p><h1 tabindex="-1">${t('homeTitle')}</h1><p class="intro">${t('homeIntro')}</p><p class="demo-disclaimer">${icon('info')}${t('homeScope')}</p><div class="stack-actions">${action('openProfile','open-profile')}</div></section>`;
}
function access() {
  const id=state.scene;
  const key=id==='credential-stale'?'stale':id;
  const primary=id==='initialize'?'initializeAction':id==='unavailable'?'retry':'returnLogin';
  const dest=id==='initialize'?'initialize-member':id==='unavailable'?'retry-service':'signin';
  return `<section class="journey-card access-card"><div class="journey-icon">${icon(id==='suspended'?'lock':'info')}</div><p class="eyebrow">${t('access')}</p><h1 tabindex="-1">${t(key+'Title')}</h1><p class="intro">${t(key+'Body')}</p>${state.notice?banner(state.notice):''}<div class="stack-actions">${action(primary,dest)}${['unavailable','initialize'].includes(id)?action('returnLogin','signin',true):''}</div></section>`;
}
function render(focus=false) {
  document.documentElement.lang=state.lang;
  document.title=`Mland · ${t('profileNav')} · Prototype 003`;
  const profileScene=state.scene.startsWith('profile');
  const authScene=['signin','signup','signin-unverified'].includes(state.scene)||state.scene.startsWith('auth-');
  root.innerHTML=`<div class="prototype-banner"><strong>${t('prototype')}</strong><span>${t('prototypeNote')}</span></div><header class="site-header"><a class="brand" href="#main" aria-label="Mland · ${t('profileNav')}">mland<span class="brand-period">.</span></a><span class="header-divider"></span><span class="header-context">${t('home')}</span><div class="header-actions"><button class="language-button" data-action="language" aria-label="${t('language')}: ${state.lang==='vi'?'English':'Tiếng Việt'}">${icon('globe')}<span>${state.lang.toUpperCase()}</span></button>${state.signedIn?`<button class="logout-button" data-action="logout">${icon('exit')}<span>${t('logout')}</span></button>`:''}</div></header><main id="main">${profileScene?profile():authScene?auth():['verify','verify-sent','verified'].includes(state.scene)?verification():state.scene==='home'?homeDestination():access()}</main><footer class="site-footer"><span>${t('footer')}</span><span>${t('footerNote')}</span></footer><div id="reviewer-root"></div>`;
  bind();
  // This live region survives page re-renders so save feedback is announced.
  requestAnimationFrame(()=>{const isAlert=['saveFailure','authError','authOutage','authRequired'].includes(state.notice);document.querySelector('#announcer').textContent=state.notice&&!isAlert?t(state.notice):'';});
  mountReviewer(document.querySelector('#reviewer-root'),{state,t,scenarios,onSelect:chooseScenario,onReset:()=>{state=freshState();epoch++;render(true);}});
  if(focus) root.querySelector('h1')?.focus({preventScroll:true});
}
function chooseScenario(id) {
  // Completing the simulated provider check must retain the unfinished registration.
  if (['verify','verify-sent'].includes(state.scene)&&['verified','credential-stale'].includes(id)) {
    state.emailVerified=true;state.credentialFresh=id==='verified';finishAuthentication();return;
  }
  epoch++;state=selectScenario(id,state.lang);render(true);window.scrollTo({top:0,behavior:'instant'});
}
function go(id) {epoch++;state.scene=id;state.busy=false;state.notice='';state.errors={};render(true);window.scrollTo({top:0,behavior:'instant'});}
function finishAuthentication() {
  if(!state.emailVerified){go('verify');return;}
  if(!eligibleMember(state.memberStatus)){state.signedIn=false;go('suspended');return;}
  if(!state.credentialFresh){state.signedIn=false;go('credential-stale');return;}
  state.hasMember=true;state.signedIn=true;go('home');
}
async function delay() {const current=epoch;await new Promise(r=>setTimeout(r,550));return current===epoch;}
function bind() {
  root.querySelectorAll('[data-action]').forEach(button=>button.addEventListener('click',event=>{
    event.preventDefault();const id=button.dataset.action;
    if(id==='language'){state.lang=state.lang==='vi'?'en':'vi';render();return;}
    if(id==='logout'){state.signedIn=false;go('signin');return;}
    if(id==='recheck'){if(state.emailVerified){finishAuthentication();return;}state.notice='verifyPending';render();return;}
    if(id==='resend'){state.resendCount++;state.notice='verifySent';render();return;}
    if(id==='finish-auth'||id==='initialize-member'){finishAuthentication();return;}
    if(id==='open-profile'){
      if(!state.signedIn){go('signin');return;}
      if(!state.emailVerified){go('verify');return;}
      if(!eligibleMember(state.memberStatus)){go('suspended');return;}
      if(!state.hasMember){go('initialize');return;}
      go('profile');return;
    }
    if(id==='retry-service'){state.notice='unavailableBody';render();return;}
    go(id);
  }));
  root.querySelector('#profile-form')?.addEventListener('input',e=>{
    if(['fullName','phone'].includes(e.target.name)) {
      state.draft[e.target.name]=e.target.value;
      if(state.notice==='saved') {state.notice='';root.querySelector('.feedback.success')?.remove();document.querySelector('#announcer').textContent='';}
    }
  });
  root.querySelector('#profile-form')?.addEventListener('submit',async e=>{
    e.preventDefault();if(state.busy)return;
    const result=validateProfile(state.draft.fullName,state.draft.phone);state.errors=result.errors;state.notice='';
    if(Object.keys(state.errors).length){render();root.querySelector('[aria-invalid="true"]')?.focus();return;}
    state.busy=true;render();if(!await delay())return;
    state.busy=false;
    if(state.failure){state.notice='saveFailure';state.failure=false;render();return;}
    state.saved={...fixture,...result.value,updatedAt:new Date().toISOString()};state.draft={fullName:result.value.fullName,phone:result.value.phone??''};state.notice='saved';state.scene='profile';render();
  });
  root.querySelector('#auth-form')?.addEventListener('submit',async e=>{
    e.preventDefault();if(state.busy)return;
    const email=root.querySelector('#email'),password=root.querySelector('#password');
    const valid=email.validity.valid&&email.value.length>0&&password.value.length>0;
    password.value=''; // Never retain credentials in state, logs or storage.
    if(!valid){state.notice='authRequired';render();root.querySelector('#email')?.focus();return;}
    const fail=state.scene==='auth-error',outage=state.scene==='auth-outage',signup=state.scene==='signup';
    state.busy=true;state.notice='authLoading';render();if(!await delay())return;
    if(fail||outage){state.busy=false;state.notice=fail?'authError':'authOutage';render();return;}
    state.credentialFresh=true;
    if(signup){state.emailVerified=false;state.signedIn=false;state.hasMember=false;state.saved=null;state.draft={fullName:'',phone:''};go('verify');state.notice='registrationEmailSent';render();return;}
    finishAuthentication();
  });
}
render();
