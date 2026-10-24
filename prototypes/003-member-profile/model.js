// Prototype-only, volatile data. Never use this as an authorization mechanism.
export const fixture = Object.freeze({email:'linh@example.com', fullName:'Nguyễn An Linh', phone:'+84912345678', updatedAt:'2026-10-19T03:30:00Z'});
export const scenarios = [
  ['signin','Đăng nhập','Sign in','FR-011'], ['signup','Đăng ký','Register','FR-011'],
  ['auth-loading','Auth · đang xử lý','Auth · loading','FR-011'], ['auth-error','Auth · sai thông tin','Auth · invalid credentials','FR-011'],
  ['auth-outage','Auth · dịch vụ lỗi','Auth · unavailable','FR-016'],
  ['verify','Email · chưa xác minh','Email · unverified','FR-003/011'], ['verify-sent','Email · gửi lại','Email · resent','FR-011'],
  ['verified','Email · đã xác minh','Email · verified','FR-011/012'],
  ['home','Trang chủ · điểm đến sau xác thực','Home · authentication destination','FR-011/015'],
  ['signin-unverified','Đăng nhập · email chưa xác minh','Sign in · unverified email','FR-011'],
  ['profile-pending-policy','Hồ sơ · Member chưa chấp thuận policy','Profile · pending policy acceptance','FR-003/015'],
  ['credential-stale','Xác minh xong · cần đăng nhập lại','Verified · fresh sign-in needed','FR-012'],
  ['profile-empty','Hồ sơ · lần đầu','Profile · first save','FR-004'], ['profile','Hồ sơ · đã có','Profile · saved','FR-004'],
  ['profile-edit','Hồ sơ · đang sửa','Profile · editing','FR-005/010'], ['profile-invalid','Hồ sơ · lỗi field','Profile · validation','FR-006/007'],
  ['profile-saving','Hồ sơ · đang lưu','Profile · saving','FR-010'], ['profile-success','Hồ sơ · lưu thành công','Profile · success','FR-010'],
  ['profile-failure','Hồ sơ · lỗi lưu','Profile · save failed','FR-009/010'],
  ['expired','Phiên · hết hạn','Session · expired','FR-012/013'], ['revoked','Phiên · bị thu hồi','Session · revoked','FR-013'],
  ['suspended','Tài khoản · bị khóa','Account · suspended','FR-003'], ['initialize','Member · cần khởi tạo','Member · initialization','FR-014/016'],
  ['unavailable','Dịch vụ · tạm lỗi','Service · unavailable','FR-016']
];
export function validateProfile(fullName, phone) {
  const name = fullName.replace(/^\p{White_Space}+|\p{White_Space}+$/gu, '');
  const errors = {};
  if (!name || [...name].length > 100 || /\p{Cc}/u.test(name)) errors.fullName = 'nameError';
  const normalizedPhone = /^\p{White_Space}*$/u.test(phone) ? null : phone;
  if (normalizedPhone !== null && (!/^\+[1-9]\d{1,14}$/.test(normalizedPhone) || /\p{Cc}/u.test(normalizedPhone))) errors.phone = 'phoneError';
  return {errors, value:{fullName:name, phone:normalizedPhone}};
}
export function freshState() {
  return {scene:'profile', lang:'vi', draft:{fullName:fixture.fullName,phone:fixture.phone}, saved:{...fixture}, errors:{}, notice:'', busy:false, failure:false, signedIn:true, emailVerified:true, memberStatus:'ACTIVE', hasMember:true, credentialFresh:true, resendCount:0};
}
export const eligibleMember = status => ['ACTIVE','PENDING_POLICY_ACCEPTANCE'].includes(status);
export function selectScenario(id, lang='vi') {
  const state=freshState(); state.lang=lang; state.scene=id;
  if (id==='profile-empty') {state.saved=null; state.draft={fullName:'',phone:''};}
  if (['profile-edit','profile-saving','profile-failure'].includes(id)) state.draft={fullName:'Nguyễn An Linh Mai',phone:'+84987654321'};
  if (id==='profile-invalid') {state.draft={fullName:'',phone:'0912 345 678'};state.errors={fullName:'nameError',phone:'phoneError'};}
  if (id==='profile-failure') {state.notice='saveFailure';state.failure=true;}
  if (id==='profile-success') state.notice='saved';
  if (['signup','signin-unverified','verify','verify-sent','verified','credential-stale'].includes(id)) {
    state.saved=null;state.draft={fullName:'',phone:''};state.memberStatus='PENDING_POLICY_ACCEPTANCE';state.hasMember=false;
  }
  if (['signup','signin-unverified','verify','verify-sent'].includes(id)) state.emailVerified=false;
  if (id==='profile-pending-policy') state.memberStatus='PENDING_POLICY_ACCEPTANCE';
  if (id==='suspended') state.memberStatus='SUSPENDED';
  if (id==='initialize') state.hasMember=false;
  if (id==='credential-stale') state.credentialFresh=false;
  state.busy=['auth-loading','profile-saving'].includes(id);
  state.signedIn=!/^(signin|signup|auth-|verify|verified|expired|revoked|credential-stale|suspended|initialize|unavailable)/.test(id);
  return state;
}
