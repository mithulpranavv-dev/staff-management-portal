const MODEL_URL = 'https://cdn.jsdelivr.net/gh/justadudewhohacks/face-api.js@0.22.2/weights';

let modelsLoaded = false;
let currentDescriptor = null;

async function loadFaceModels() {
    if (modelsLoaded) return;
    await Promise.all([
        faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
        faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
        faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL)
    ]);
    modelsLoaded = true;
}

async function startFaceCamera(videoElementId) {
    const video = document.getElementById(videoElementId);
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true });
        video.srcObject = stream;
    } catch (err) {
        console.error('Camera access error:', err);
        throw new Error('CAMERA_ERROR');
    }

    try {
        await loadFaceModels();
    } catch (err) {
        console.error('Face model loading error:', err);
        throw new Error('MODEL_ERROR');
    }

    return true;
}

async function captureFaceDescriptor(videoElementId) {
    const video = document.getElementById(videoElementId);

    const detection = await faceapi
        .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions())
        .withFaceLandmarks()
        .withFaceDescriptor();

    if (!detection) {
        return null;
    }

    return Array.from(detection.descriptor);
}

function descriptorToCsv(descriptorArray) {
    return descriptorArray.join(',');
}