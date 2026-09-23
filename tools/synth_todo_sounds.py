"""Synthesize Todo's rhythm and clap sound layers as OGG/Vorbis assets.

The generator is intentionally deterministic and keeps the samples in the repository's
existing tools/ convention, so the audio can be re-rendered after tuning without relying
on an external editor. It uses numpy + soundfile, like synth_mega_sounds.py.
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "jujutsumod", "sounds", "todo")


def norm(x, peak=0.52):
    """Normalize with headroom for Vorbis decode overshoot."""
    maximum = np.max(np.abs(x))
    return x / maximum * peak if maximum > 0 else x


def declick(x, attack=0.003, release=0.035):
    n_in = min(int(attack * SR), len(x))
    n_out = min(int(release * SR), len(x))
    if n_in:
        x[:n_in] *= np.linspace(0.0, 1.0, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1.0, 0.0, n_out)
    return x


def low_pass(x, cutoff):
    """Small one-pole FIR to keep the rattle physical instead of digital noise."""
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    length = min(len(x), 512)
    impulse = np.power(a, np.arange(length))
    impulse *= 1.0 - a
    return np.convolve(x, impulse)[:len(x)]


def seamless_loop(x, crossfade_seconds=0.09):
    """Make the first and last crossfade windows identical at the loop seam."""
    width = min(int(crossfade_seconds * SR), len(x) // 2)
    if width == 0:
        return x
    blend = x[-width:] * np.linspace(1.0, 0.0, width) + x[:width] * np.linspace(0.0, 1.0, width)
    x[-width:] = blend
    x[:width] = blend
    return x


def vibraslap_rattle():
    """A roughly two-second, seamless granular rattle bed for Revised Boogie Woogie."""
    duration = 2.0
    t = np.arange(int(SR * duration)) / SR
    rng = np.random.default_rng(0xB01C13)

    noise = rng.standard_normal(len(t))
    noise = low_pass(noise, 4300)
    tremolo = 0.72 + 0.28 * np.sin(2.0 * np.pi * 7.0 * t + 0.7)
    rattles = noise * tremolo * (0.20 + 0.08 * np.sin(2.0 * np.pi * 2.0 * t))

    ticks = np.zeros_like(t)
    tick_times = np.arange(0.035, duration, 0.075)
    for start in tick_times:
        index = int(start * SR)
        length = min(int(0.028 * SR), len(t) - index)
        if length <= 0:
            continue
        u = np.arange(length) / SR
        burst = rng.standard_normal(length) * np.exp(-u / 0.008)
        burst += 0.35 * np.sin(2.0 * np.pi * (960.0 + 140.0 * np.sin(start * 4.0)) * u)
        ticks[index:index + length] += burst * 0.48

    x = np.tanh((rattles + ticks) * 1.7)
    return norm(seamless_loop(x), 0.48)


def dense_clap():
    """A short, denser clap layer used at high rhythm beats."""
    duration = 0.30
    t = np.arange(int(SR * duration)) / SR
    rng = np.random.default_rng(0xC1A0)
    snap = rng.standard_normal(len(t)) * np.exp(-t / 0.018)
    snap = low_pass(snap, 5800)
    body = np.sin(2.0 * np.pi * (145.0 - 38.0 * t / duration) * t) * np.exp(-t / 0.11)
    body += 0.35 * np.sin(2.0 * np.pi * 290.0 * t) * np.exp(-t / 0.055)
    tail = rng.standard_normal(len(t)) * np.exp(-t / 0.075) * 0.18
    return norm(declick(np.tanh((snap * 1.3 + body + tail) * 1.25), 0.001, 0.045), 0.52)


def peak_chime():
    """A bright, short two-part chime for Peak activation."""
    duration = 0.72
    t = np.arange(int(SR * duration)) / SR
    x = np.zeros_like(t)
    for start, frequency, amplitude, decay in (
            (0.0, 523.25, 0.72, 0.24),
            (0.12, 783.99, 0.62, 0.28),
            (0.24, 1046.50, 0.46, 0.34)):
        u = np.maximum(t - start, 0.0)
        gate = (t >= start).astype(float)
        note = np.sin(2.0 * np.pi * frequency * u)
        note += 0.23 * np.sin(2.0 * np.pi * 2.01 * frequency * u)
        note += 0.08 * np.sin(2.0 * np.pi * 3.0 * frequency * u)
        x += gate * amplitude * note * np.exp(-u / decay)
    return norm(declick(np.tanh(x * 1.1), 0.001, 0.08), 0.48)


def main():
    os.makedirs(ROOT, exist_ok=True)
    sounds = (
        ("vibraslap_rattle.ogg", vibraslap_rattle()),
        ("clap_dense.ogg", dense_clap()),
        ("peak_chime.ogg", peak_chime()),
    )
    for name, data in sounds:
        path = os.path.join(ROOT, name)
        sf.write(path, data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print(name, len(data) / SR * 1000.0, "ms", os.path.getsize(path), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
