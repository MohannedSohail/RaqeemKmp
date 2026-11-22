const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');

console.log('=== Step 1: Generating Master Synthesized Soundtrack ===');
execSync('python scripts/generate_master_soundtrack.py', { stdio: 'inherit' });

console.log('=== Step 2: Starting Render HTTP Server ===');
const htmlContent = fs.readFileSync(path.join(__dirname, 'render_motion_video.html'), 'utf8');

const rawWebmPath = path.join(__dirname, '..', 'docs', 'marketing', 'raw_animation.webm');
const outVertical = path.join(__dirname, '..', 'docs', 'marketing', 'raqeem_cinematic_launch_reels_9x16.mp4');
const outLandscape = path.join(__dirname, '..', 'docs', 'marketing', 'raqeem_cinematic_launch_linkedin_16x9.mp4');
const outSquare = path.join(__dirname, '..', 'docs', 'marketing', 'raqeem_cinematic_launch_feed_1x1.mp4');
const soundtrackPath = path.join(__dirname, '..', 'docs', 'marketing', 'master_soundtrack.wav');

let chromeProcess;

const server = http.createServer((req, res) => {
    if (req.url === '/' || req.url === '/index.html') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(htmlContent);
    } else if (req.url === '/upload' && req.method === 'POST') {
        console.log('Receiving recorded animation stream from Chrome headless...');
        const fileStream = fs.createWriteStream(rawWebmPath);
        req.pipe(fileStream);
        req.on('end', () => {
            res.end('ok');
            console.log('Raw animation stream successfully recorded and saved to:', rawWebmPath);
            if (chromeProcess) {
                chromeProcess.kill();
            }
            server.close(() => {
                postProcessVideos();
            });
        });
    } else {
        res.writeHead(404);
        res.end('Not found');
    }
});

server.listen(8088, '127.0.0.1', () => {
    console.log('Render Server listening on http://127.0.0.1:8088');
    const chromePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
    console.log('=== Step 3: Launching Chrome Headless for High-FPS Render ===');
    chromeProcess = spawn(chromePath, [
        '--headless=new',
        '--no-sandbox',
        '--disable-gpu',
        '--use-fake-ui-for-media-stream',
        '--autoplay-policy=no-user-gesture-required',
        '--window-size=1080,1920',
        'http://127.0.0.1:8088'
    ]);

    chromeProcess.on('exit', (code) => {
        console.log('Chrome process exited with code:', code);
    });
});

function postProcessVideos() {
    console.log('=== Step 4: Transcoding and Muxing with Multi-Format FFmpeg ===');

    // 1. Vertical 9:16 (1080x1920) for Instagram Reels, Stories, TikTok
    console.log('1. Encoding 9:16 Instagram Reel: ' + outVertical);
    execSync(`ffmpeg -y -i "${rawWebmPath}" -i "${soundtrackPath}" -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p -c:a aac -b:a 256k -shortest "${outVertical}"`, { stdio: 'inherit' });

    // 2. Square 1:1 (1080x1080) for LinkedIn Feed & Instagram Feed
    console.log('2. Encoding 1:1 Square Feed Video: ' + outSquare);
    execSync(`ffmpeg -y -i "${rawWebmPath}" -i "${soundtrackPath}" -vf "scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2:color=0x060911" -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p -c:a aac -b:a 256k -shortest "${outSquare}"`, { stdio: 'inherit' });

    // 3. Landscape 16:9 (1920x1080) for LinkedIn, YouTube, X
    console.log('3. Encoding 16:9 Landscape Video: ' + outLandscape);
    execSync(`ffmpeg -y -i "${rawWebmPath}" -i "${soundtrackPath}" -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0x060911" -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p -c:a aac -b:a 256k -shortest "${outLandscape}"`, { stdio: 'inherit' });

    // Remove raw intermediate webm file
    if (fs.existsSync(rawWebmPath)) {
        fs.unlinkSync(rawWebmPath);
    }

    console.log('=== ALL LAUNCH VIDEOS SUCCESSFULLY GENERATED! ===');
    console.log('Reels (9:16):', outVertical);
    console.log('Square (1:1):', outSquare);
    console.log('Landscape (16:9):', outLandscape);
    process.exit(0);
}
