"""One-off generator for the shikigami quick-selector UI sounds (issue #109).

Four restrained tactile roles -- open, hover, select, reject -- synthesized as short
low-passed body resonances (pluck / tick / two-note confirm / thud) and written as OGG
Vorbis via soundfile/libsndfile. Kept in tools/ so the selector voice can be re-rendered
or re-tuned without hunting for a chat transcript.

Design rule from the spec: no beeps. Every role is a damped resonance band-limited well
below the sharp 1-4 kHz "system alert" region, and the whole set peaks politely (Vorbis
overshoots on decode -- see synth_mega_sounds.py).
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "jujutsumod", "sounds", "megumi")


def norm(x, peak):
    """Normalize with headroom: Vorbis overshoots peaks on decode."""
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x


def low_pass(x, cutoff):
    """One-pole low-pass, applied as a truncated exponential FIR (keeps the loop out of Python)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    n = min(len(x), 512)
    if a <= 0.0:
        return x
    impulse = np.power(a, np.arange(n))
    if impulse[-1] > 1e-6:  # short signal, cutoff so low the tail would not have decayed
        n = len(x)
        impulse = np.power(a, np.arange(n))
    h = (1.0 - a) * impulse
    return np.convolve(x, h)[:len(x)]


def declick(x, attack=0.002, release=0.012):
    """Ramp in/out so the sample never starts or ends on a discontinuity."""
    n_in = min(int(attack * SR), len(x))
    n_out = min(int(release * SR), len(x))
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def open_sound():
    """~90 ms warm low-plucked body: the strip sliding in."""
    T = 0.09
    t = np.arange(int(SR * T)) / SR
    f = 196.0  # G3
    body = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.032)
    body += 0.34 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / 0.015)
    body += 0.16 * np.sin(2 * np.pi * 3 * f * t) * np.exp(-t / 0.009)

    rng = np.random.default_rng(0x5E1EC7)
    pick = rng.standard_normal(len(t)) * np.exp(-t / 0.003) * 0.22  # soft finger attack

    x = low_pass(np.tanh((body + pick) * 1.2), 1500)
    x *= 0.35 + 0.65 * np.exp(-t / 0.05)
    return norm(declick(x), 0.5)


def hover_sound():
    """~45 ms soft tick for movement between entries; quietest role, fires most often."""
    T = 0.045
    t = np.arange(int(SR * T)) / SR
    f = 620.0
    x = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.006)
    x += 0.30 * np.sin(2 * np.pi * 1.5 * f * t) * np.exp(-t / 0.0035)
    x += 0.12 * np.sin(2 * np.pi * 0.5 * f * t) * np.exp(-t / 0.009)
    x = low_pass(x, 2100)
    return norm(declick(x, attack=0.0015, release=0.008), 0.34)


def select_sound():
    """~120 ms two-note confirm (E4 -> B4, a clean fifth), plucked."""
    T = 0.12
    t = np.arange(int(SR * T)) / SR
    x = np.zeros_like(t)
    for start, f, tau, amp in ((0.0, 329.63, 0.024, 0.75), (0.042, 493.88, 0.040, 1.0)):
        u = np.maximum(t - start, 0.0)
        gate = (t >= start).astype(float)
        note = np.sin(2 * np.pi * f * u) + 0.28 * np.sin(2 * np.pi * 2 * f * u) * np.exp(-u / 0.012)
        note += 0.12 * np.sin(2 * np.pi * 3 * f * u) * np.exp(-u / 0.007)
        x += amp * gate * note * np.exp(-u / tau)
    x = low_pass(np.tanh(x * 1.15), 1900)
    return norm(declick(x), 0.46)


def reject_sound():
    """~80 ms muted thud for an unavailable entry: low, dull, no note."""
    T = 0.08
    t = np.arange(int(SR * T)) / SR
    f = 96.0 - 26.0 * np.minimum(t / T, 1.0)  # small downward bend reads as "refused"
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.022)
    body += 0.4 * np.sin(2 * np.pi * 2 * np.cumsum(f) / SR) * np.exp(-t / 0.010)

    rng = np.random.default_rng(0x7A11E1)
    knock = low_pass(rng.standard_normal(len(t)), 480) * np.exp(-t / 0.007) * 0.45

    x = low_pass(np.tanh((body + knock) * 1.4), 850)
    return norm(declick(x), 0.42)


def main():
    os.makedirs(ROOT, exist_ok=True)
    roles = (("selector_open.ogg", open_sound()),
             ("selector_hover.ogg", hover_sound()),
             ("selector_select.ogg", select_sound()),
             ("selector_reject.ogg", reject_sound()))
    for name, data in roles:
        path = os.path.join(ROOT, name)
        sf.write(path, data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print(name, len(data) / SR * 1000.0, "ms", os.path.getsize(path), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
