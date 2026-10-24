// Review controls are not part of the product UI or its authorization flow.
export function mountReviewer(root,{state,t,scenarios,onSelect,onReset}) {
  root.dataset.resendCount=String(state.resendCount);
  root.innerHTML=`<details class="reviewer"><summary><span class="reviewer-dot"></span>${t('reviewer')}<span class="reviewer-code">003</span></summary><div class="reviewer-body"><p>${t('reviewerHint')}</p><label for="scenario">${t('scenario')}</label><select id="scenario">${scenarios.map(([id,vi,en,fr])=>`<option value="${id}" ${state.scene===id?'selected':''}>${state.lang==='vi'?vi:en} · ${fr}</option>`).join('')}</select><div class="reviewer-actions"><button type="button" id="apply-scene">${t('apply')}</button><button type="button" id="reset-demo">${t('reset')}</button></div><p class="reviewer-path">${t('firstStep')}</p></div></details>`;
  root.querySelector('#apply-scene').addEventListener('click',()=>onSelect(root.querySelector('#scenario').value));
  const context=document.createElement('p');context.textContent=`${t('reviewerContext')}: ${state.memberStatus} · ${t('policyIndependent')}`;root.querySelector('.reviewer-body').append(context);
  root.querySelector('#reset-demo').addEventListener('click',onReset);
}
