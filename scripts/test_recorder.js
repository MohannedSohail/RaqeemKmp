const http = require('http');
const fs = require('fs');
const { spawn } = require('child_process');

const HTML = `
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="margin:0; background:#0E131F;">
<canvas id="c" width="1080" height="1920"></canvas>
<script>
const canvas = document.getElementById('c');
const ctx = canvas.getContext('2d');
const stream = canvas.captureStream(30);
const recorder = new MediaRecorder(stream, { mimeType: 'video/webm' });
const chunks = [];
recorder.ondataavailable = e => { if (e.data.size > 0) chunks.push(e.data); };
recorder.onstop = async () => {
    const blob = new Blob(chunks, { type: 'video/webm' });
    await fetch('/upload', { method: 'POST', body: blob });
    document.body.style.background = '#00C48C';
};
recorder.start();

let start = performance.now();
function draw() {
    let elapsed = (performance.now() - start) / 1000;
    ctx.fillStyle = '#0E131F';
    ctx.fillRect(0, 0, 1080, 1920);
    ctx.fillStyle = '#00C48C';
    ctx.beginPath();
    ctx.arc(540, 960 + Math.sin(elapsed * 5) * 200, 100, 0, Math.PI * 2);
    ctx.fill();
    if (elapsed < 2.0) {
        requestAnimationFrame(draw);
    } else {
        recorder.stop();
    }
}
requestAnimationFrame(draw);
</script>
</body>
</html>
`;

const server = http.createServer((req, res) => {
    if (req.url === '/') {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(HTML);
    } else if (req.url === '/upload' && req.method === 'POST') {
        const fileStream = fs.createWriteStream('docs/marketing/test_recorded.webm');
        req.pipe(fileStream);
        req.on('end', () => {
            res.end('ok');
            console.log('Video received and saved!');
            if (chromeProcess) chromeProcess.kill();
            server.close(() => process.exit(0));
        });
    }
});

let chromeProcess;
server.listen(8085, '127.0.0.1', () => {
    console.log('Server started on 8085');
    const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
    chromeProcess = spawn(chromePath, [
        '--headless=new',
        '--no-sandbox',
        '--disable-gpu',
        '--use-fake-ui-for-media-stream',
        '--autoplay-policy=no-user-gesture-required',
        '--window-size=1080,1920',
        'http://127.0.0.1:8085'
    ]);
});
