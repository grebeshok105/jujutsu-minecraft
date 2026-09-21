"""Generator for the black hole soundscape OGGs (debug visual experiment).

Four layers, all synthesized — no external samples, no licensing surface:

  prelude.ogg  ~1.1 s pre-reveal pressure: world-air sucking out + sub tone being born
  drone.ogg    ~16 s positional mass loop: sub-bass stack + slow swells + dread texture
  inner.ogg    ~16 s non-positional "inside the head" layer: deeper, narrower, closer
  impulse.ogg  ~0.8 s disappearance jolt: sharp displacement crack + sub drop, hard cut

Written as OGG Vorbis via soundfile/libsndfile, same convention as synth_mega_sounds.py.
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "jujutsumod", "sounds", "blackhole")


def norm(x, peak=0.5):
    """Normalize with heavy headroom: Vorbis overshoots peaks on decode."""
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x


def lowpass(x, alpha):
    """One-pole lowpass; alpha scalar or per-sample array (~0.002-0.3)."""
    y = np.empty_like(x)
    acc = 0.0
    if np.isscalar(alpha):
        for i in range(len(x)):
            acc += alpha * (x[i] - acc)
            y[i] = acc
    else:
        for i in range(len(x)):
            acc += alpha[i] * (x[i] - acc)
            y[i] = acc
    return y


def loopify(x, fade=0.35):
    """Crossfade the tail into the head so OpenAL looping has no seam."""
    n = int(len(x) * fade)
    head = x[:n].copy()
    tail = x[-n:]
    t = np.linspace(0, 1, n)
    blended = tail * t + head * (1 - t)
    return np.concatenate([x[:-n], blended])


def prelude():
    T = 1.15
    t = np.linspace(0, T, int(SR * T), endpoint=False)
    p = t / T
    rng = np.random.default_rng(0xB1AC)

    # Air leaving: broadband noise sucked downward (lowpass closing over time).
    air = rng.standard_normal(len(t))
    air = lowpass(air, 0.30 - 0.24 * p) * (0.5 + 0.5 * p)

    # Sub tone being born: 24 Hz rising to 38 Hz, swelling in.
    f = 24 + 14 * p
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * (p ** 1.6) * 0.9

    # Pressure wobble: slow amplitude throb, irregular.
    wob = 1.0 + 0.30 * np.sin(2 * np.pi * 3.7 * t + 1.0) + 0.15 * np.sin(2 * np.pi * 6.3 * t)

    out = (air * 0.35 + sub) * wob
    out[-int(0.03 * SR):] *= np.linspace(1, 0.25, int(0.03 * SR))  # hard-ish tail into the reveal
    return norm(np.tanh(out * 1.3))


def drone():
    T = 16.0
    t = np.linspace(0, T, int(SR * T), endpoint=False)
    rng = np.random.default_rng(0xD20E)

    # Sub stack: 27 Hz fundamental + detuned partials — the "mass" of the sound.
    sub = (np.sin(2 * np.pi * 27 * t)
           + 0.55 * np.sin(2 * np.pi * 40.5 * t + 0.7)
           + 0.30 * np.sin(2 * np.pi * 54.3 * t + 2.1))

    # Slow swells: two incommensurate LFOs, never a short loop.
    swell = (0.72 + 0.20 * np.sin(2 * np.pi * t / 9.3 + 0.4)
             + 0.12 * np.sin(2 * np.pi * t / 5.1 + 2.2))

    # Dread texture: filtered noise with a slow irregular tremolo — "agony of hell" air,
    # kept quiet so it reads as pressure, not as a wind sample.
    tex = lowpass(rng.standard_normal(len(t)), 0.012)
    trem = 0.5 + 0.5 * np.sin(2 * np.pi * t / 3.7 + np.sin(2 * np.pi * t / 11.0))
    tex *= trem * 0.16

    # Rare irregular surges: seeded random events, not a beat.
    surge = np.zeros_like(t)
    for start_s, width_s, amp in ((3.1, 0.9, 0.35), (7.7, 1.4, 0.28), (12.4, 0.7, 0.42)):
        m = (t > start_s) & (t < start_s + width_s)
        surge[m] += amp * np.sin(np.pi * (t[m] - start_s) / width_s) ** 2

    out = (sub * 0.85 + tex) * (swell + surge)
    return norm(np.tanh(loopify(out) * 1.15))


def impulse():
    T = 1.6
    t = np.linspace(0, T, int(SR * T), endpoint=False)
    rng = np.random.default_rng(0x1E9C)

    # Displacement crack: a fast noise transient, not a trailer boom.
    crack = np.tanh(rng.standard_normal(len(t)) * np.exp(-t / 0.045) * 2.6) * 0.8

    # Sub drop: 70 Hz -> 24 Hz in ~0.3 s — the mass leaving.
    f = 24 + 46 * np.exp(-t / 0.13)
    drop = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.30)

    # Residual ring: a low 52 Hz tone decaying over ~1.2 s — the "stunned ringing" after the
    # snap, quiet enough to read as near-silence.
    ring = np.sin(2 * np.pi * 52 * t) * np.exp(-t / 0.55) * 0.22
    ring += np.sin(2 * np.pi * 78 * t + 0.7) * np.exp(-t / 0.40) * 0.10

    out = crack + drop * 1.1 + ring
    out[int(0.5 * SR):] *= np.linspace(1, 0, len(t) - int(0.5 * SR)) ** 1.6  # hard cut into silence
    return norm(np.tanh(out * 1.2))


def main():
    os.makedirs(ROOT, exist_ok=True)
    for name, data in (("prelude.ogg", prelude()),
                       ("impulse.ogg", impulse())):
        path = os.path.join(ROOT, name)
        sf.write(path, data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print(name, os.path.getsize(path), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
