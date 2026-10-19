"""把 app/src/main/res/raw 下的四段环境声做得更柔和、更慢一点。

只依赖 Python 标准库（wave + array + math）：
  * 一阶低通：削掉雨声 / 海浪里刺耳的高频；
  * 0.88–0.92 倍速重采样：整体变慢、音高略降；
  * 循环接缝做 0.35 秒交叠：避免每 12 秒“咔”一下；
  * 归一化到统一的 RMS / 峰值，四段响度接近。

用法：python tools/process_ambience.py
输出直接覆盖 app/src/main/res/raw 下的同名文件（先 git diff 看一眼再提交）。
"""
from __future__ import annotations

import array
import math
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app" / "src" / "main" / "res" / "raw"

# 文件: (低通截止 Hz, 速度, 目标 RMS, 峰值上限)
CONFIG = {
    "focus_rain.wav": (3200.0, 0.90, 0.115, 0.82),
    "focus_waves.wav": (2200.0, 0.88, 0.115, 0.82),
    "focus_fire.wav": (2400.0, 0.92, 0.110, 0.82),
    "focus_cafe.wav": (2600.0, 0.92, 0.105, 0.82),
}

XFADE_SECONDS = 0.35


def lowpass(samples: list[float], fs: int, cutoff: float) -> list[float]:
    alpha = 1.0 - math.exp(-2.0 * math.pi * cutoff / fs)
    y = 0.0
    out: list[float] = []
    for value in samples:
        y += alpha * (value - y)
        out.append(y)
    return out


def slower(samples: list[float], speed: float) -> list[float]:
    """speed < 1 -> 更长、更慢、音高略低。"""
    target_len = int(len(samples) / speed)
    last = len(samples) - 1
    out: list[float] = []
    for i in range(target_len):
        pos = i * speed
        j = int(pos)
        if j >= last:
            out.append(samples[last])
            continue
        frac = pos - j
        out.append(samples[j] * (1.0 - frac) + samples[j + 1] * frac)
    return out


def crossfade_loop(samples: list[float], fs: int, seconds: float) -> list[float]:
    """把尾巴叠到开头：循环从 out[-1] 回到 out[0] 时是连续的。"""
    fade = int(fs * seconds)
    if fade * 2 >= len(samples):
        return samples
    out_len = len(samples) - fade
    out: list[float] = []
    for i in range(out_len):
        if i < fade:
            w = i / fade
            out.append(samples[i] * w + samples[out_len + i] * (1.0 - w))
        else:
            out.append(samples[i])
    return out


def process(name: str) -> None:
    cutoff, speed, target_rms, peak_ceiling = CONFIG[name]
    path = SRC / name
    with wave.open(str(path), "rb") as reader:
        params = reader.getparams()
        raw = reader.readframes(reader.getnframes())

    data = array.array("h")
    data.frombytes(raw)
    fs = params.framerate
    mono = [x / 32768.0 for x in data]

    mono = lowpass(mono, fs, cutoff)
    mono = slower(mono, speed)
    mono = crossfade_loop(mono, fs, XFADE_SECONDS)

    rms = math.sqrt(sum(v * v for v in mono) / len(mono))
    peak = max(abs(v) for v in mono)
    gain = min(target_rms / rms, peak_ceiling / peak, 3.0)
    scaled = [max(-1.0, min(1.0, v * gain)) for v in mono]

    out = array.array("h", (int(round(v * 32767.0)) for v in scaled))
    with wave.open(str(path), "wb") as writer:
        writer.setnchannels(params.nchannels)
        writer.setsampwidth(params.sampwidth)
        writer.setframerate(fs)
        writer.writeframes(out.tobytes())

    print(
        f"{name}: {len(data) / fs:.2f}s -> {len(out) / fs:.2f}s, "
        f"rms {rms:.3f}->{math.sqrt(sum(v * v for v in scaled) / len(scaled)):.3f}, "
        f"peak {peak:.3f}->{max(abs(v) for v in scaled):.3f}, gain {gain:.2f}"
    )


if __name__ == "__main__":
    for filename in CONFIG:
        process(filename)
