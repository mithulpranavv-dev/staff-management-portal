let state = {
    username: '',
    verifiedOtp: ''
};

const step1Form = document.getElementById('step1Form');
const step2Form = document.getElementById('step2Form');
const step3Form = document.getElementById('step3Form');

const stepBadge1 = document.getElementById('stepBadge1');
const stepBadge2 = document.getElementById('stepBadge2');
const stepBadge3 = document.getElementById('stepBadge3');

const errorAlert = document.getElementById('errorAlert');
const successAlert = document.getElementById('successAlert');

function clearAlerts() {
    errorAlert.classList.add('d-none');
    successAlert.classList.add('d-none');
}

function showError(msg) {
    clearAlerts();
    errorAlert.textContent = msg;
    errorAlert.classList.remove('d-none');
}

function showSuccess(msg) {
    clearAlerts();
    successAlert.textContent = msg;
    successAlert.classList.remove('d-none');
}

// STEP 1: Request OTP
step1Form.addEventListener('submit', async function (e) {
    e.preventDefault();
    clearAlerts();

    const inputVal = document.getElementById('emailOrUsername').value.trim();
    if (!inputVal) {
        showError('Please enter your username.');
        return;
    }

    state.username = inputVal;

    try {
        const response = await fetch('/api/auth/forgot-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: inputVal })
        });

        const resText = await response.text();

        if (!response.ok) {
            throw new Error(resText || 'Failed to generate OTP.');
        }

        showSuccess(resText || `OTP sent! Please check your inbox.`);

        step1Form.classList.add('d-none');
        step2Form.classList.remove('d-none');

        stepBadge1.className = 'badge bg-success rounded-circle p-2 px-3';
        stepBadge2.className = 'badge bg-primary rounded-circle p-2 px-3';

    } catch (err) {
        showError(err.message);
    }
});

// STEP 2: Verify OTP
step2Form.addEventListener('submit', async function (e) {
    e.preventDefault();
    clearAlerts();

    const otp = document.getElementById('otpInput').value.trim();
    if (!otp || otp.length < 6) {
        showError('Please enter the 6-digit verification code.');
        return;
    }

    try {
        const response = await fetch('/api/auth/verify-otp', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: state.username,
                otp: otp
            })
        });

        const resText = await response.text();

        if (!response.ok) {
            throw new Error(resText || 'Invalid or expired OTP.');
        }

        state.verifiedOtp = otp;

        showSuccess('OTP verified successfully! Now set your new password.');

        step2Form.classList.add('d-none');
        step3Form.classList.remove('d-none');

        stepBadge2.className = 'badge bg-success rounded-circle p-2 px-3';
        stepBadge3.className = 'badge bg-primary rounded-circle p-2 px-3';

    } catch (err) {
        showError(err.message);
    }
});

// STEP 3: Reset Password
step3Form.addEventListener('submit', async function (e) {
    e.preventDefault();
    clearAlerts();

    const newPass = document.getElementById('resetNewPassword').value;
    const confirmPass = document.getElementById('resetConfirmPassword').value;

    if (newPass !== confirmPass) {
        showError('Passwords do not match. Please re-enter.');
        return;
    }

    if (newPass.length < 6) {
        showError('Password must be at least 6 characters.');
        return;
    }

    try {
        const response = await fetch('/api/auth/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: state.username,
                otp: state.verifiedOtp,
                newPassword: newPass
            })
        });

        const resText = await response.text();

        if (!response.ok) {
            throw new Error(resText || 'Failed to reset password.');
        }

        showSuccess('Password reset successfully! Redirecting to login...');

        setTimeout(() => {
            window.location.href = 'index.html?msg=Password+reset+successfully.+Please+sign+in+with+your+new+password.';
        }, 1500);

    } catch (err) {
        showError(err.message);
    }
});