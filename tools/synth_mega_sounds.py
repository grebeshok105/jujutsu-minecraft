"""One-off generator for the Mega Nail charge riser and launch blast OGGs.

Layered numpy synthesis (sub ramp + detuned saw stack + shaped noise), written
as OGG Vorbis via soundfile/libsndfile. Kept in tools/ so the sounds can be
re-rendered or re-tuned without hunting for a chat transcript.
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "jujutsumod", "sounds", "nobara")


def norm(x, peak=0.5):
    """Normalize with heavy headroom: Vorbis overshoots peaks on decode (seen 1.86 from 0.95)."""
    m = np.max(np.abs(x))
    return x / m * peak if m > 0 else x

def charge_riser():
    T = 1.3
    t = np.linspace(0, T, int(SR * T), endpoint=False)
    p = t / T

    f_sub = 50 * (105 / 50) ** p
    sub = np.sin(2 * np.pi * np.cumsum(f_sub) / SR) * (0.35 + 0.45 * p)

    saws = np.zeros_like(t)
    for det in (0.988, 1.0, 1.013):
        f = 110 * (460 / 110) ** p * det
        ph = np.cumsum(f) / SR
        saws += 2.0 * (ph % 1.0) - 1.0
    saws = np.tanh(saws * 1.6) * (0.12 + 0.5 * p ** 1.6)

    rng = np.random.default_rng(0x4E61A1)
    noise = np.diff(rng.standard_normal(len(t) + 1))
    noise *= (p ** 2.4) * 0.55
    noise *= 1.0 + 0.35 * np.sin(2 * np.pi * 16 * t * (0.5 + p))

    snap = np.zeros_like(t)
    n_snap = int(0.07 * SR)
    snap[-n_snap:] = rng.standard_normal(n_snap) * np.linspace(0, 1, n_snap) ** 2 * 0.9

    riser = (sub + saws + noise + snap) * (0.25 + 0.75 * p ** 1.5)
    riser[-int(0.012 * SR):] *= np.linspace(1, 0, int(0.012 * SR))
    return norm(np.tanh(riser * 1.25))


def launch_blast():
    T = 1.1
    t = np.linspace(0, T, int(SR * T), endpoint=False)
    rng = np.random.default_rng(0x7A1E5F)

    burst = np.tanh(rng.standard_normal(len(t)) * np.exp(-t / 0.11) * 3.2) * 0.9

    f_boom = 38 + (105 - 38) * np.exp(-t / 0.22)
    boom = np.sin(2 * np.pi * np.cumsum(f_boom) / SR) * np.exp(-t / 0.4)

    tail = np.convolve(rng.standard_normal(len(t)), np.ones(96) / 96.0, mode="same")
    tail *= np.exp(-t / 0.55) * (1.0 + 0.45 * np.sin(2 * np.pi * 14 * t)) * 0.8

    blast = norm(np.tanh((burst + boom + tail) * 1.15))
    blast[-int(0.02 * SR):] *= np.linspace(1, 0, int(0.02 * SR))
    return blast


def main():
    os.makedirs(ROOT, exist_ok=True)
    for name, data in (("mega_charge_riser.ogg", charge_riser()),
                       ("mega_launch_blast.ogg", launch_blast())):
        path = os.path.join(ROOT, name)
        sf.write(path, data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print(name, os.path.getsize(path), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
