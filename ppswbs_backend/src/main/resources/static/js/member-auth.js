/**
 * Mland Member Authentication Client Integration
 */
document.addEventListener('DOMContentLoaded', () => {
    const btnGoogle = document.getElementById('btnGoogle');
    const btnSignIn = document.getElementById('btnSignIn');
    const btnRegister = document.getElementById('btnRegister');
    const btnForgotPassword = document.getElementById('btnForgotPassword');
    const acceptCheck = document.getElementById('acceptPolicyCheck');
    const errorMessage = document.getElementById('errorMessage');

    const btnSubmitPolicy = document.getElementById('btnSubmitPolicy');
    const btnConfirmImport = document.getElementById('btnConfirmImport');
    const btnDeclineImport = document.getElementById('btnDeclineImport');

    function showError(msg) {
        if (errorMessage) {
            errorMessage.textContent = msg || 'Đã xảy ra lỗi trong quá trình xác thực.';
            errorMessage.style.display = 'block';
        } else {
            alert(msg);
        }
    }

    function clearError() {
        if (errorMessage) {
            errorMessage.style.display = 'none';
        }
    }

    async function sendProvisionRequest(idToken) {
        try {
            const resp = await fetch('/api/v1/members/me', {
                method: 'PUT',
                headers: {
                    'Authorization': 'Bearer ' + idToken,
                    'Content-Type': 'application/json'
                }
            });

            if (resp.status === 200 || resp.status === 201) {
                const member = await resp.json();
                const returnTo = document.getElementById('returnTo')?.value || '/';
                if (member.status === 'PENDING_POLICY_ACCEPTANCE') {
                    window.location.href = '/member-auth/policy-acceptance?returnTo=' + encodeURIComponent(returnTo);
                } else {
                    window.location.href = returnTo;
                }
            } else if (resp.status === 409) {
                showError('Địa chỉ email này thuộc về một tài khoản đã tồn tại với phương thức đăng nhập khác.');
            } else {
                const errData = await resp.json();
                showError(errData.message || 'Xác thực thất bại.');
            }
        } catch (e) {
            console.error(e);
            showError('Lỗi kết nối máy chủ.');
        }
    }

    if (btnSubmitPolicy) {
        btnSubmitPolicy.addEventListener('click', async () => {
            const check = document.getElementById('acceptCheck');
            if (!check || !check.checked) {
                alert('Bạn cần chọn đồng ý với Điều khoản và Chính sách để tiếp tục.');
                return;
            }
            const returnTo = document.getElementById('returnTo')?.value || '/';
            // In full Firebase deployment, client gets fresh token and calls POST /api/v1/members/me/policy-acceptances
            window.location.href = returnTo;
        });
    }

    if (btnConfirmImport) {
        btnConfirmImport.addEventListener('click', () => {
            const returnTo = document.getElementById('returnTo')?.value || '/';
            window.location.href = returnTo;
        });
    }

    if (btnDeclineImport) {
        btnDeclineImport.addEventListener('click', () => {
            const returnTo = document.getElementById('returnTo')?.value || '/';
            window.location.href = returnTo;
        });
    }
});
