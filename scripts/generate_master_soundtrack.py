import math
import struct
import wave
import os

def create_master_soundtrack(output_wav="docs/marketing/master_soundtrack.wav", duration=21.5, sample_rate=44100):
    num_samples = int(duration * sample_rate)
    bpm = 120.0
    beat_sec = 60.0 / bpm
    sixteenth_sec = beat_sec / 4.0

    chord_prog = [
        [185.00, 220.00, 277.18, 329.63], # F#m7
        [146.83, 220.00, 293.66, 369.99], # Dmaj7
        [246.94, 293.66, 369.99, 440.00], # Bm7
        [220.00, 277.18, 329.63, 415.30], # Aadd9
    ]

    import random
    random.seed(42)
    noise_table = [random.uniform(-1.0, 1.0) for _ in range(sample_rate)]

    audio_left = [0.0] * num_samples
    audio_right = [0.0] * num_samples

    for i in range(num_samples):
        t = i / sample_rate
        beat_idx = int(t / beat_sec)
        bar_idx = int(beat_idx / 4)
        chord = chord_prog[bar_idx % len(chord_prog)]
        time_in_beat = t % beat_sec
        time_in_16th = t % sixteenth_sec

        # 1. Ambient Synth Pad
        pad = 0.0
        for fi, freq in enumerate(chord):
            detune = 1.0 + 0.002 * math.sin(2.0 * math.pi * 3.0 * t + fi)
            w = math.sin(2.0 * math.pi * freq * detune * t)
            w += 0.3 * math.sin(4.0 * math.pi * freq * detune * t)
            pad += w
        pad = pad * 0.09

        # 2. 808 Sub-bass Kick Drum (on beats 0 & 2 of 4)
        kick = 0.0
        bar_beat = beat_idx % 4
        if bar_beat in [0, 2]:
            t_k = time_in_beat
            if t_k < 0.28:
                f_k = 42.0 + 90.0 * math.exp(-t_k * 22.0)
                phase_k = 2.0 * math.pi * f_k * t_k
                env_k = math.exp(-t_k * 13.0)
                kick = math.sin(phase_k) * env_k * 0.50

        # 3. Snare / Finger Snap (on beats 1 & 3)
        snare = 0.0
        if bar_beat in [1, 3]:
            t_s = time_in_beat
            if t_s < 0.22:
                noise_val = noise_table[int((t_s * sample_rate * 3.3)) % len(noise_table)]
                body = math.sin(2.0 * math.pi * 190.0 * t_s) * math.exp(-t_s * 25.0)
                env_s = math.exp(-t_s * 16.0)
                snare = (0.65 * noise_val + 0.35 * body) * env_s * 0.30

        # 4. Hi-Hat 16th notes
        hihat = 0.0
        if time_in_16th < 0.045 and t > 1.0:
            noise_val = noise_table[int((time_in_16th * sample_rate * 4.9)) % len(noise_table)]
            accent = 1.3 if (int(t / sixteenth_sec) % 4 == 2) else 0.75
            env_hh = math.exp(-time_in_16th * 85.0)
            hihat = noise_val * env_hh * 0.10 * accent

        # 5. Precisely Synced Foley Sound Effects:
        sfx = 0.0

        # A. Mic Tap Click at 1.5s
        dt_mic = t - 1.5
        if 0.0 <= dt_mic < 0.3:
            sfx += math.sin(2.0 * math.pi * 880.0 * dt_mic) * math.exp(-dt_mic * 35.0) * 0.35
            sfx += math.sin(2.0 * math.pi * 1760.0 * dt_mic) * math.exp(-dt_mic * 25.0) * 0.20

        # B. Voice typing mechanical clicks (1.6s - 3.0s)
        if 1.6 <= t < 3.0:
            dt_type = (t - 1.6) % 0.12
            if dt_type < 0.025:
                sfx += noise_table[int(dt_type * sample_rate * 2.0) % len(noise_table)] * math.exp(-dt_type * 120.0) * 0.12

        # C. Cash Register / Coins Bell at 3.8s
        dt_coin = t - 3.8
        if 0.0 <= dt_coin < 0.6:
            sfx += math.sin(2.0 * math.pi * 2093.0 * dt_coin) * math.exp(-dt_coin * 8.0) * 0.35
            sfx += math.sin(2.0 * math.pi * 2637.0 * dt_coin) * math.exp(-dt_coin * 10.0) * 0.25

        # D. Split Badge Snap at 6.0s
        dt_split = t - 6.0
        if 0.0 <= dt_split < 0.4:
            sfx += math.sin(2.0 * math.pi * 1318.5 * dt_split) * math.exp(-dt_split * 12.0) * 0.25

        # E. Weekly Chart Glissando (9.5s - 10.5s)
        if 9.5 <= t < 10.5:
            bar_idx = int((t - 9.5) / 0.14)
            dt_bar = (t - 9.5) % 0.14
            scale_notes = [523.25, 587.33, 659.25, 698.46, 783.99, 880.00, 987.77]
            if bar_idx < len(scale_notes) and dt_bar < 0.1:
                f_bar = scale_notes[bar_idx]
                sfx += math.sin(2.0 * math.pi * f_bar * dt_bar) * math.exp(-dt_bar * 15.0) * 0.18

        # F. Excel Export Success Bell at 12.0s
        dt_excel = t - 12.0
        if 0.0 <= dt_excel < 0.8:
            sfx += math.sin(2.0 * math.pi * 1567.98 * dt_excel) * math.exp(-dt_excel * 6.0) * 0.30
            sfx += math.sin(2.0 * math.pi * 3135.96 * dt_excel) * math.exp(-dt_excel * 8.0) * 0.15

        # G. Shield Lock Clack at 14.5s
        dt_lock = t - 14.5
        if 0.0 <= dt_lock < 0.35:
            sfx += math.sin(2.0 * math.pi * 330.0 * dt_lock) * math.exp(-dt_lock * 28.0) * 0.40
            sfx += noise_table[int(dt_lock * sample_rate) % len(noise_table)] * math.exp(-dt_lock * 30.0) * 0.25

        # H. Grand Hero Logo Swell at 17.5s
        dt_logo = t - 17.5
        if 0.0 <= dt_logo < 1.2:
            sfx += math.sin(2.0 * math.pi * 1174.66 * dt_logo) * math.exp(-dt_logo * 4.0) * 0.30
            sfx += math.sin(2.0 * math.pi * 1760.00 * dt_logo) * math.exp(-dt_logo * 4.5) * 0.25

        # Transition whooshes on scene cuts (4.5s, 9.0s, 13.5s, 17.5s)
        for cut_t in [4.5, 9.0, 13.5, 17.5]:
            dt_cut = t - (cut_t - 0.35)
            if 0.0 <= dt_cut < 0.45:
                sweep = math.sin(math.pi * (dt_cut / 0.45))
                sfx += noise_table[int(dt_cut * sample_rate) % len(noise_table)] * sweep * 0.12

        # Master Stereo Mix
        mix_l = pad + kick + snare + hihat + sfx * 0.95
        mix_r = pad + kick + snare + hihat + sfx * 1.05

        # Fade in & out
        fade = 1.0
        if t < 0.6:
            fade = t / 0.6
        elif t > (duration - 1.0):
            fade = max(0.0, (duration - t) / 1.0)

        mix_l *= fade
        mix_r *= fade

        audio_left[i] = max(-0.95, min(0.95, mix_l))
        audio_right[i] = max(-0.95, min(0.95, mix_r))

    os.makedirs(os.path.dirname(output_wav), exist_ok=True)
    with wave.open(output_wav, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        packed = bytearray()
        for l, r in zip(audio_left, audio_right):
            packed.extend(struct.pack("<hh", int(l * 32767.0), int(r * 32767.0)))
        wf.writeframes(packed)
    print(f"Master soundtrack generated: {output_wav} ({duration}s)")

if __name__ == "__main__":
    create_master_soundtrack()
