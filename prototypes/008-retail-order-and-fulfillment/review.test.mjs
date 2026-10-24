import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const files=['index.html','styles.css','app.js','README.md'];
for(const file of files){const text=await readFile(new URL(file,import.meta.url),'utf8');assert.ok(text.trim().length>0,`${file} must not be empty`)}
const html=await readFile(new URL('index.html',import.meta.url),'utf8');
const js=await readFile(new URL('app.js',import.meta.url),'utf8');
assert.match(html,/lang="vi"/);
for(const scene of ['catalogue','cart','checkout','success','orders','tracking','staff'])assert.ok(js.includes(scene),`missing scene: ${scene}`);
for(const term of ['VNPay','GHTK','Mland Quận 1','Mland Thủ Đức'])assert.ok(js.includes(term),`missing fixture: ${term}`);
assert.ok(!/(fetch\(|XMLHttpRequest|localStorage|sessionStorage)/.test(js),'prototype must not call network or persist data');
console.log(JSON.stringify({passed:files.length,scenes:7,networkCalls:0},null,2));
