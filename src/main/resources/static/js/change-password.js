document.addEventListener('DOMContentLoaded', () => {
    const urlParams = new URLSearchParams(window.location.search);
    const forced = urlParams.get('forced') === 'true' || localStorage.getItem('mustChangePassword') === 'true';

    const forcedAlert = document.getElementById('forcedAlert');
    const cancelBtn = document.getElementById('cancelBtn');

    if (forced) {
        if (forcedAlert) forcedAlert.classList.remove('d-none');
        if (cancelBtn) cancelBtn.classList.add('d-none');
    }
});

document.getElementById('changePasswordForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const oldPassword = document.getElementById('oldPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;

    const errorAlert = document.getElementById('errorAlert');
    const successAlert = document.getElementById('successAlert');

    errorAlert.classList.add('d-none');
    successAlert.classList.add('d-none');

    if (newPassword !== confirmPassword) {
        errorAlert.textContent = 'New password and confirmation password do not match.';
        errorAlert.classList.remove('d-none');
        return;
    }

    if (newPassword.length < 6) {
        errorAlert.textContent = 'New password must be at least 6 characters long.';
        errorAlert.classList.remove('d-none');
        return;
    }

    const username = localStorage.getItem('username') || '';
    const token = localStorage.getItem('token');

    try {
        const response = await fetch('/api/auth/change-password', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(token ? { 'Authorization': `Bearer ${token}` } : {})
            },
            body: JSON.stringify({
                username,
                oldPassword,
                newPassword
            })
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || 'Failed to update password.');
        }

        localStorage.removeItem('mustChangePassword');
        successAlert.textContent = 'Password updated successfully! Redirecting...';
        successAlert.classList.remove('d-none');

        setTimeout(() => {
            window.location.href = 'dashboard.html';
        }, 1500);

    } catch (err) {
        errorAlert.textContent = err.message;
        errorAlert.classList.remove('d-none');
    }
});