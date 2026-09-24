"""Generate original, deterministic audition sounds for the offline tabletop prototype.
No samples, external assets, or runtime synthesis dependencies. Python standard library only.
Run from any directory: python design/audio/generate_tabletop.py
"""
from pathlib import Path
import math
import random
import struct
import wave

RATE = 22050
OUT = Path(__file__).resolve().parents[2] / 'app/src/main/res/raw'

def render(name, duration, impacts=(), notes=()):
    rng = random.Random(name)
    samples = [0.0] * int(duration * RATE)
    for start, gain, pitch, decay in impacts:
        offset = int(start * RATE)
        low = 0.0
        for i in range(min(int(decay * 8 * RATE), len(samples) - offset)):
            t = i / RATE
            low = low * .55 + rng.uniform(-1, 1) * .45
            envelope = math.exp(-t / decay) * min(1, t / .001)
            body = math.sin(2 * math.pi * pitch * t) * .55
            overtone = math.sin(2 * math.pi * pitch * 2.71 * t) * .20
            samples[offset+i] += (low * .4 + body + overtone) * envelope * gain
    for start, frequency, gain, decay in notes:
        offset = int(start * RATE)
        for i in range(min(int(decay * 7 * RATE), len(samples) - offset)):
            t = i / RATE
            samples[offset+i] += math.sin(2 * math.pi * frequency * t) * math.exp(-t / decay) * min(1, t / .006) * gain
    # Leave headroom and fade endpoints; audition gain is controlled by the game mixer.
    peak = max(max(abs(s) for s in samples), .001)
    gain = min(1, .65 / peak)
    pcm = []
    for i, sample in enumerate(samples):
        fade = min(1, i / (RATE * .003), (len(samples)-1-i) / (RATE*.015))
        pcm.append(struct.pack('<h', int(max(-1, min(1, sample * gain * fade)) * 32767)))
    with wave.open(str(OUT / f'tabletop_{name}.wav'), 'wb') as out:
        out.setparams((1, 2, RATE, len(samples), 'NONE', 'not compressed'))
        out.writeframes(b''.join(pcm))

OUT.mkdir(parents=True, exist_ok=True)
render('dice_roll', .64, [(t, g, f, .016) for t, g, f in [(.05,.3,610),(.14,.35,710),(.24,.4,540),(.34,.32,800),(.46,.65,440),(.54,.2,650)]])
render('piece_move', .13, [(0,.45,740,.013)])
render('capture', .28, [(0,.65,290,.028),(.022,.35,970,.016)])
render('piece_finish', .55, notes=[(0,660,.35,.12),(.10,880,.30,.13),(.20,1320,.22,.12)])
render('extra_roll', .30, notes=[(0,740,.22,.07),(.08,988,.20,.10)])
render('turn_skip', .22, notes=[(0,440,.16,.065),(.07,330,.13,.07)])
render('invalid_action', .14, [(0,.18,230,.020)])
render('win', 1.0, notes=[(0,523.25,.28,.2),(.12,659.25,.25,.2),(.24,783.99,.23,.2),(.40,1046.5,.23,.25)])
