var token = localStorage.getItem('token');

const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const context = canvas.getContext('2d');
const resultBox = document.getElementById('resultBox');
const scanAgainBtn = document.getElementById('scanAgainBtn');

let scanning = true;
let stream = null;

function showResult(message, type) {
    resultBox.className = `alert alert-${type} mt-3`;
    resultBox.textContent = message;
    resultBox.classList.remove('d-none');
}

async function startCamera() {
    try {
        stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment' }
        });
        video.srcObject = stream;
        video.setAttribute('playsinline', true);
        video.play();
        requestAnimationFrame(tick);
    } catch (err) {
        showResult('Camera access denied or unavailable. Please allow camera permission and reload.', 'danger');
    }
}

function tick() {
    if (!scanning) return;

    if (video.readyState === video.HAVE_ENOUGH_DATA) {
        canvas.height = video.videoHeight;
        canvas.width = video.videoWidth;
        context.drawImage(video, 0, 0, canvas.width, canvas.height);

        const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
        const code = jsQR(imageData.data, imageData.width, imageData.height);

        if (code && code.data) {
            scanning = false;
            handleScan(code.data);
            return;
        }
    }

    requestAnimationFrame(tick);
}

async function handleScan(scannedText) {
    const parts = scannedText.split(':');
    if (parts.length !== 2) {
        showResult('Invalid QR code format.', 'danger');
        offerRetry();
        return;
    }

    const [empId, qrToken] = parts;

    try {
        const response = await fetch('/api/attendance/scan', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({ empId: Number(empId), token: qrToken })
        });

        const data = await response.json();

        if (!response.ok) {
            showResult(data.error || 'Scan failed.', 'danger');
            offerRetry();
            return;
        }

        stopCamera();
        showResult(
            `Marked successfully — empId ${data.empId}, Status: ${data.status}`,
            data.status === 'Half Day' ? 'warning' : 'success'
        );
        scanAgainBtn.classList.remove('d-none');
    } catch (err) {
        showResult('Something went wrong. Please try again.', 'danger');
        offerRetry();
    }
}

function offerRetry() {
    setTimeout(() => {
        scanning = true;
        requestAnimationFrame(tick);
    }, 2000);
}

function stopCamera() {
    if (stream) {
        stream.getTracks().forEach(t => t.stop());
    }
}

scanAgainBtn.addEventListener('click', () => {
    resultBox.classList.add('d-none');
    scanAgainBtn.classList.add('d-none');
    scanning = true;
    startCamera();
});

startCamera();