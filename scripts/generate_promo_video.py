import math
import struct
import wave
import os
import subprocess

def create_soundtrack(output_wav="docs/marketing/soundtrack.wav", duration=21.0, sample_rate=44100):
    """
    Generates a professional 120 BPM FinTech electronic soundtrack with sound design
    (808 kicks, crisp snares, hi-hats, ambient synth pads, and transition UI chimes)
    using only Python standard library.
    """
    num_samples = int(duration * sample_rate)
    bpm = 120.0
    beat_sec = 60.0 / bpm  # 0.5 seconds per beat
    sixteenth_sec = beat_sec / 4.0

    chord_prog = [
        [185.00, 220.00, 277.18, 329.63], # F#m7
        [146.83, 220.00, 293.66, 369.99], # Dmaj7
        [246.94, 293.66, 369.99, 440.00], # Bm7
        [220.00, 277.18, 329.63, 415.30], # Aadd9
    ]

    import random
    noise_table = [random.uniform(-1.0, 1.0) for _ in range(sample_rate)]

    audio_left = [0.0] * num_samples
    audio_right = [0.0] * num_samples

    transition_times = [0.0, 4.2, 8.4, 12.6, 16.8]

    for i in range(num_samples):
        t = i / sample_rate
        beat_idx = int(t / beat_sec)
        bar_idx = int(beat_idx / 4)
        chord = chord_prog[bar_idx % len(chord_prog)]
        time_in_beat = t % beat_sec
        time_in_16th = t % sixteenth_sec

        # 1. Synth Pad / Arp
        pad = 0.0
        for fi, freq in enumerate(chord):
            detune = 1.0 + 0.002 * math.sin(2.0 * math.pi * 3.0 * t + fi)
            wave_val = math.sin(2.0 * math.pi * freq * detune * t)
            wave_val += 0.3 * math.sin(4.0 * math.pi * freq * detune * t)
            pad += wave_val
        pad = pad * 0.08

        # 2. Kick Drum
        kick = 0.0
        bar_beat = beat_idx % 4
        if bar_beat in [0, 2]:
            t_kick = time_in_beat
            if t_kick < 0.25:
                f_kick = 45.0 + 85.0 * math.exp(-t_kick * 25.0)
                phase_kick = 2.0 * math.pi * f_kick * t_kick
                env_kick = math.exp(-t_kick * 14.0)
                kick = math.sin(phase_kick) * env_kick * 0.45

        # 3. Snare / Clap
        snare = 0.0
        if bar_beat in [1, 3]:
            t_snare = time_in_beat
            if t_snare < 0.2:
                noise_val = noise_table[int((t_snare * sample_rate * 3.7)) % len(noise_table)]
                body = math.sin(2.0 * math.pi * 180.0 * t_snare) * math.exp(-t_snare * 30.0)
                env_snare = math.exp(-t_snare * 18.0)
                snare = (0.7 * noise_val + 0.3 * body) * env_snare * 0.25

        # 4. Hi-Hat
        hihat = 0.0
        t_hh = time_in_16th
        if t_hh < 0.05 and t > 1.0:
            noise_val = noise_table[int((t_hh * sample_rate * 5.1)) % len(noise_table)]
            accent = 1.3 if (int(t / sixteenth_sec) % 4 == 2) else 0.7
            env_hh = math.exp(-t_hh * 90.0)
            hihat = noise_val * env_hh * 0.09 * accent

        # 5. UI Confirmation Chime on Scene Transitions
        chime = 0.0
        for tt in transition_times:
            dt = t - tt
            if 0.0 <= dt < 0.8:
                f_chime = 1760.0
                env_chime = math.exp(-dt * 6.0)
                chime += math.sin(2.0 * math.pi * f_chime * dt) * env_chime * 0.18
                chime += math.sin(4.0 * math.pi * f_chime * dt) * env_chime * 0.08

        # 6. Gentle Whoosh / Sweep on cuts
        whoosh = 0.0
        for tt in transition_times[1:]:
            dt = t - (tt - 0.4)
            if 0.0 <= dt < 0.5:
                noise_val = noise_table[int((dt * sample_rate)) % len(noise_table)]
                sweep_env = math.sin(math.pi * (dt / 0.5))
                whoosh += noise_val * sweep_env * 0.06

        mix_left = pad + kick + snare + hihat + chime * 0.9 + whoosh * 0.8
        mix_right = pad + kick + snare + hihat + chime * 1.1 + whoosh * 1.2

        fade_env = 1.0
        if t < 0.8:
            fade_env = t / 0.8
        elif t > (duration - 1.2):
            fade_env = max(0.0, (duration - t) / 1.2)

        mix_left *= fade_env
        mix_right *= fade_env

        audio_left[i] = max(-0.95, min(0.95, mix_left))
        audio_right[i] = max(-0.95, min(0.95, mix_right))

    os.makedirs(os.path.dirname(output_wav), exist_ok=True)
    with wave.open(output_wav, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        packed_data = bytearray()
        for l, r in zip(audio_left, audio_right):
            val_l = int(l * 32767.0)
            val_r = int(r * 32767.0)
            packed_data.extend(struct.pack("<hh", val_l, val_r))
        wf.writeframes(packed_data)
    print(f"Soundtrack generated: {output_wav} ({duration}s)")


def render_promo_videos():
    """
    Renders 2 high-quality videos:
    1. Vertical 9:16 (1080x1920) for Instagram Reels / Stories
    2. Square 1:1 (1080x1080) for LinkedIn / Instagram Feed
    """
    images = [
        "docs/marketing/screenshot_01_voice_v2.jpg",
        "docs/marketing/screenshot_02_reports_export_v2.jpg",
        "docs/marketing/screenshot_03_split_history_v2.jpg",
        "docs/marketing/screenshot_04_budget_radar_v2.jpg",
        "docs/marketing/screenshot_05_offline_privacy_v2.jpg"
    ]
    soundtrack = "docs/marketing/soundtrack.wav"

    for img in images:
        if not os.path.exists(img):
            raise FileNotFoundError(f"Missing image: {img}")

    # Clip durations:
    # 5 clips of 4.8s each, with 0.6s xfade transition between them:
    # Total duration = 5 * 4.8 - 4 * 0.6 = 24.0 - 2.4 = 21.6s (trimmed to 21s)
    clip_dur = 4.8
    trans_dur = 0.6

    # 1. Render Vertical Reel (1080x1920)
    print("=== Rendering 9:16 Instagram Reel Video ===")
    out_vertical = "docs/marketing/raqeem_launch_reel_vertical.mp4"

    # Filter graph for vertical 1080x1920
    # Inputs 0..4 are the 5 images, input 5 is the soundtrack
    filter_complex_vertical = (
        # Scale and pad each image with subtle pan/motion
        f"[0:v]scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v0];"
        f"[1:v]scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v1];"
        f"[2:v]scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v2];"
        f"[3:v]scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v3];"
        f"[4:v]scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v4];"
        # Transitions using xfade
        f"[v0][v1]xfade=transition=smoothleft:duration={trans_dur}:offset=4.2[x1];"
        f"[x1][v2]xfade=transition=slideleft:duration={trans_dur}:offset=8.4[x2];"
        f"[x2][v3]xfade=transition=circleopen:duration={trans_dur}:offset=12.6[x3];"
        f"[x3][v4]xfade=transition=fadeblack:duration={trans_dur}:offset=16.8[vfinal]"
    )

    cmd_vertical = [
        "ffmpeg", "-y",
        "-loop", "1", "-t", str(clip_dur), "-i", images[0],
        "-loop", "1", "-t", str(clip_dur), "-i", images[1],
        "-loop", "1", "-t", str(clip_dur), "-i", images[2],
        "-loop", "1", "-t", str(clip_dur), "-i", images[3],
        "-loop", "1", "-t", str(clip_dur), "-i", images[4],
        "-i", soundtrack,
        "-filter_complex", filter_complex_vertical,
        "-map", "[vfinal]",
        "-map", "5:a",
        "-c:v", "libx264",
        "-preset", "medium",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "192k",
        "-t", "21.0",
        out_vertical
    ]

    subprocess.run(cmd_vertical, check=True)
    print(f"Successfully generated: {out_vertical}")

    # 2. Render Square Feed Video (1080x1080) for LinkedIn and Instagram Feed
    print("=== Rendering 1:1 LinkedIn / Feed Video ===")
    out_square = "docs/marketing/raqeem_launch_feed_square.mp4"

    filter_complex_square = (
        f"[0:v]scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v0];"
        f"[1:v]scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v1];"
        f"[2:v]scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v2];"
        f"[3:v]scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v3];"
        f"[4:v]scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2:color=0x0E131F,setsar=1,fps=30[v4];"
        f"[v0][v1]xfade=transition=smoothleft:duration={trans_dur}:offset=4.2[x1];"
        f"[x1][v2]xfade=transition=slideleft:duration={trans_dur}:offset=8.4[x2];"
        f"[x2][v3]xfade=transition=circleopen:duration={trans_dur}:offset=12.6[x3];"
        f"[x3][v4]xfade=transition=fadeblack:duration={trans_dur}:offset=16.8[vfinal]"
    )

    cmd_square = [
        "ffmpeg", "-y",
        "-loop", "1", "-t", str(clip_dur), "-i", images[0],
        "-loop", "1", "-t", str(clip_dur), "-i", images[1],
        "-loop", "1", "-t", str(clip_dur), "-i", images[2],
        "-loop", "1", "-t", str(clip_dur), "-i", images[3],
        "-loop", "1", "-t", str(clip_dur), "-i", images[4],
        "-i", soundtrack,
        "-filter_complex", filter_complex_square,
        "-map", "[vfinal]",
        "-map", "5:a",
        "-c:v", "libx264",
        "-preset", "medium",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "192k",
        "-t", "21.0",
        out_square
    ]

    subprocess.run(cmd_square, check=True)
    print(f"Successfully generated: {out_square}")


if __name__ == "__main__":
    create_soundtrack()
    render_promo_videos()
