#!/usr/bin/env python3
"""将情绪曲线 CSV 渲染为 SVG 图表与事件详情页（无第三方依赖）。"""

from __future__ import annotations

import argparse
import csv
import html
import json
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Tuple


PHASE_COLORS = {
    "warmup": "#DFF4E5",
    "conflict": "#FFE3E3",
    "repair": "#E3EDFF",
    "normal_play": "#F2F2F2",
    "pressure_burst": "#FFEFE0",
    "repair_window": "#E8F8FF",
    "normal": "#F2F2F2",
    "pressure": "#FFEFE0",
    "session": "#F8F8F8",
    "dating_start": "#E7F8EA",
    "money_pressure": "#FFF1E6",
    "betrayal_hint": "#FFE4EC",
    "breakup": "#FDE2E2",
    "aftershock": "#EEF2FF",
    "unknown": "#F6F6F6",
}

PHASE_LABELS = {
    "warmup": "养成预热",
    "conflict": "冲突阶段",
    "repair": "修复阶段",
    "normal_play": "常规互动",
    "pressure_burst": "压力突发",
    "repair_window": "修复窗口",
    "normal": "常规互动",
    "pressure": "高压阶段",
    "session": "单次会话",
    "dating_start": "恋爱升温",
    "money_pressure": "经济压力",
    "betrayal_hint": "出轨疑云",
    "breakup": "分手对话",
    "aftershock": "余震冷静",
    "unknown": "未知阶段",
}

SCENARIO_LABELS = {
    "java_arc": "Java 原生关系弧线",
    "java_arc_curve": "Java 原生关系弧线",
    "java_archetype_caring": "玩家画像：关怀型",
    "java_archetype_neutral": "玩家画像：中性型",
    "java_archetype_toxic": "玩家画像：高压型",
    "java_auto_6000": "Java 自动回放 6000 步",
    "ms_arc": "MS 脚本关系弧线",
    "ms_arc_curve": "MS 脚本关系弧线",
    "ms_auto_5000": "MS 自动回放 5000 步",
    "ms_story_breakup_betrayal": "MS 剧情样例：贫困分手与出轨疑云",
    "ms_story_breakup_betrayal_curve": "MS 剧情样例：贫困分手与出轨疑云",
}

SCENARIO_DESCRIPTIONS = {
    "java_arc_curve": "阶段为养成预热 -> 冲突 -> 修复，用于验证恢复能力与压力弹性。",
    "java_archetype_caring": "正向互动占比高、冲突较少，预期 pleasure/bond 整体更高。",
    "java_archetype_neutral": "正负事件相对均衡，曲线通常在中位区间波动。",
    "java_archetype_toxic": "负向互动与社交压力较高，主要观察 pressure 抬升与关系下探。",
    "java_auto_6000": "长时随机回放，包含压力突发与修复窗口，用于看稳定性。",
    "ms_arc_curve": "由 Maidscript 驱动同类弧线，验证脚本规则的可解释性。",
    "ms_auto_5000": "脚本驱动长回放，观察 DSL 模型是否稳定且有心境切换。",
    "ms_story_breakup_betrayal_curve": "恋爱升温后进入经济压力、出轨疑云与分手余震，观察沉浸式剧情下的情绪轨迹。",
}

EVENT_LABELS = {
    "builtin.player.knee_pillow": "膝枕",
    "builtin.player.gift_flower": "送花",
    "builtin.player.attack_maid": "攻击女仆",
    "builtin.player.chat_to_maid": "对话",
    "builtin.maid.sleep_start": "睡眠恢复",
    "knee_pillow": "膝枕",
    "gift_flower": "送花",
    "attack_maid": "攻击女仆",
    "chat_to_maid": "对话",
    "sleep_start": "睡眠恢复",
    "hug": "拥抱",
    "feed": "喂食",
    "work_help": "工作协助",
}


def resolve_chart_key(source_name: str) -> str:
    return Path(source_name).stem


def resolve_scenario_label(chart_key: str, raw_scenario: str) -> str:
    scenario_key = (raw_scenario or "").strip()
    if scenario_key in SCENARIO_LABELS:
        return SCENARIO_LABELS[scenario_key]
    if chart_key in SCENARIO_LABELS:
        return SCENARIO_LABELS[chart_key]
    return scenario_key or chart_key


def resolve_scenario_desc(chart_key: str) -> str:
    return SCENARIO_DESCRIPTIONS.get(chart_key, "自动化回放曲线，用于检查情绪与关系是否随事件发生可解释变化。")


def resolve_event_label(event_id: str) -> str:
    raw = (event_id or "").strip()
    if raw in EVENT_LABELS:
        return EVENT_LABELS[raw]
    if raw.startswith("builtin."):
        tail = raw.split(".")[-1]
        return EVENT_LABELS.get(tail, tail)
    return raw or "未知事件"


def to_float(value: str, default: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def pressure_of(row: Dict[str, float | str]) -> float:
    return clamp01(float(row.get("annoyed", 0.0)) + float(row.get("jealous", 0.0)))


def read_rows(path: Path) -> List[Dict[str, float | str]]:
    rows: List[Dict[str, float | str]] = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for r in reader:
            step = int(to_float(r.get("step", "0"), 0.0))
            phase = (r.get("phase") or "").strip() or "unknown"
            event_id = (r.get("event_id") or "").strip()
            rows.append(
                {
                    "step": step,
                    "phase": phase,
                    "event_id": event_id,
                    "event_label": resolve_event_label(event_id),
                    "intensity": clamp01(to_float(r.get("intensity", "0"))),
                    "rivals": int(to_float(r.get("rivals", "0"), 0.0)),
                    "last_other_affection": clamp01(to_float(r.get("last_other_affection", "0"))),
                    "mean_other_favor": clamp01(to_float(r.get("mean_other_favor", "0"))),
                    "note": (r.get("note") or "").strip(),
                    "event_detail": (r.get("event_detail") or "").strip(),
                    "scenario": (r.get("scenario") or "").strip(),
                    "mood_top": (r.get("mood_top") or "").strip(),
                    "score": to_float(r.get("score", "0")),
                    "favor": clamp01(to_float(r.get("favor", "0"))),
                    "bond": clamp01(to_float(r.get("bond", "0"))),
                    "pleasure": clamp01(to_float(r.get("pleasure", "0"))),
                    "annoyed": clamp01(to_float(r.get("annoyed", "0"))),
                    "jealous": clamp01(to_float(r.get("jealous", "0"))),
                }
            )
    rows.sort(key=lambda x: int(x["step"]))
    return rows


def phase_segments(rows: List[Dict[str, float | str]]) -> List[Tuple[int, int, str]]:
    if not rows:
        return []
    segments: List[Tuple[int, int, str]] = []
    start = int(rows[0]["step"])
    last_step = start
    current = str(rows[0]["phase"])
    for row in rows[1:]:
        step = int(row["step"])
        phase = str(row["phase"])
        if phase != current:
            segments.append((start, last_step, current))
            start = step
            current = phase
        last_step = step
    segments.append((start, last_step, current))
    return segments


def x_map(step: int, x0: float, width: float, min_step: int, max_step: int) -> float:
    if max_step <= min_step:
        return x0
    return x0 + (step - min_step) * width / (max_step - min_step)


def y_map(value: float, y0: float, height: float) -> float:
    return y0 + (1.0 - clamp01(value)) * height


def polyline(rows: List[Dict[str, float | str]], key: str, x0: float, y0: float, width: float, height: float, min_step: int, max_step: int) -> str:
    pts: List[str] = []
    for row in rows:
        step = int(row["step"])
        val = float(row[key])
        pts.append(f"{x_map(step, x0, width, min_step, max_step):.2f},{y_map(val, y0, height):.2f}")
    return " ".join(pts)


def pressure_polyline(rows: List[Dict[str, float | str]], x0: float, y0: float, width: float, height: float, min_step: int, max_step: int) -> str:
    pts: List[str] = []
    for row in rows:
        step = int(row["step"])
        pressure = pressure_of(row)
        pts.append(f"{x_map(step, x0, width, min_step, max_step):.2f},{y_map(pressure, y0, height):.2f}")
    return " ".join(pts)


def chart_background(
    segments: List[Tuple[int, int, str]],
    x0: float,
    y0: float,
    width: float,
    height: float,
    min_step: int,
    max_step: int,
) -> str:
    lines: List[str] = []

    for s0, s1, phase in segments:
        px0 = x_map(s0, x0, width, min_step, max_step)
        px1 = x_map(s1, x0, width, min_step, max_step)
        color = PHASE_COLORS.get(phase, "#F6F6F6")
        lines.append(
            f'<rect x="{px0:.2f}" y="{y0:.2f}" width="{max(1.0, px1 - px0):.2f}" height="{height:.2f}" fill="{color}" opacity="0.65" />'
        )

    for i in range(5):
        v = i * 0.25
        y = y_map(v, y0, height)
        lines.append(f'<line x1="{x0:.2f}" y1="{y:.2f}" x2="{(x0 + width):.2f}" y2="{y:.2f}" stroke="#D9D9D9" stroke-width="1" />')
        lines.append(f'<text x="{(x0 - 8):.2f}" y="{(y + 4):.2f}" font-size="11" text-anchor="end" fill="#666">{v:.2f}</text>')

    lines.append(f'<rect x="{x0:.2f}" y="{y0:.2f}" width="{width:.2f}" height="{height:.2f}" fill="none" stroke="#666" stroke-width="1.2"/>')

    tick_count = 8
    for i in range(tick_count + 1):
        step = int(min_step + (max_step - min_step) * i / tick_count)
        x = x_map(step, x0, width, min_step, max_step)
        lines.append(f'<line x1="{x:.2f}" y1="{(y0 + height):.2f}" x2="{x:.2f}" y2="{(y0 + height + 5):.2f}" stroke="#666" stroke-width="1"/>')
        lines.append(f'<text x="{x:.2f}" y="{(y0 + height + 18):.2f}" font-size="11" text-anchor="middle" fill="#666">{step}</text>')

    return "\n".join(lines)


def event_count_summary(rows: List[Dict[str, float | str]]) -> List[Tuple[str, str, int]]:
    stat: Dict[str, int] = defaultdict(int)
    for row in rows:
        stat[str(row["event_id"])] += 1
    sorted_items = sorted(stat.items(), key=lambda x: (-x[1], x[0]))
    return [(event_id, resolve_event_label(event_id), count) for event_id, count in sorted_items]


def render_svg(rows: List[Dict[str, float | str]], source_name: str, out_path: Path) -> None:
    if not rows:
        out_path.write_text("<svg xmlns='http://www.w3.org/2000/svg' width='1200' height='200'><text x='10' y='30'>无数据</text></svg>", encoding="utf-8")
        return

    chart_key = resolve_chart_key(source_name)
    min_step = int(rows[0]["step"])
    max_step = int(rows[-1]["step"])
    scenario_label = resolve_scenario_label(chart_key, str(rows[0]["scenario"]))
    scenario_desc = resolve_scenario_desc(chart_key)
    segments = phase_segments(rows)

    width = 1480
    height = 1024
    top_pad = 112
    left_pad = 86
    right_pad = 36
    panel_h = 330
    panel_gap = 120
    panel_w = width - left_pad - right_pad
    panel1_y = top_pad
    panel2_y = panel1_y + panel_h + panel_gap

    p1_bg = chart_background(segments, left_pad, panel1_y, panel_w, panel_h, min_step, max_step)
    p2_bg = chart_background(segments, left_pad, panel2_y, panel_w, panel_h, min_step, max_step)

    p1_pleasure = polyline(rows, "pleasure", left_pad, panel1_y, panel_w, panel_h, min_step, max_step)
    p1_pressure = pressure_polyline(rows, left_pad, panel1_y, panel_w, panel_h, min_step, max_step)
    p2_favor = polyline(rows, "favor", left_pad, panel2_y, panel_w, panel_h, min_step, max_step)
    p2_bond = polyline(rows, "bond", left_pad, panel2_y, panel_w, panel_h, min_step, max_step)

    legend_phases = sorted({str(s[2]) for s in segments})
    legend_parts: List[str] = []
    lx = left_pad
    ly = height - 56
    for phase in legend_phases:
        color = PHASE_COLORS.get(phase, "#F6F6F6")
        label = PHASE_LABELS.get(phase, phase)
        legend_parts.append(f'<rect x="{lx:.2f}" y="{(ly - 10):.2f}" width="14" height="14" fill="{color}" stroke="#AAA"/>')
        legend_parts.append(f'<text x="{(lx + 20):.2f}" y="{ly:.2f}" font-size="12" fill="#444">{html.escape(label)}</text>')
        lx += 130

    tip_x = width - 470
    tip_y = 82
    tips = [
        "TIP: 所有指标都在 0~1 区间，越接近 1 越强。",
        "TIP: 数值接近 0 代表该状态弱，接近 1 代表该状态强。",
        "TIP: pleasure 越高，情绪越积极。",
        "TIP: pressure=annoyed+jealous，越高代表负面压力越强。",
        "TIP: favor=长期好感趋势，bond=关系稳定度。",
    ]
    tip_box_h = 24 + len(tips) * 18
    tip_lines: List[str] = []
    for i, t in enumerate(tips):
        tip_lines.append(
            f'<text x="{(tip_x + 14):.2f}" y="{(tip_y + 24 + i * 18):.2f}" font-size="12" fill="#334155">{html.escape(t)}</text>'
        )

    event_summary = event_count_summary(rows)[:4]
    event_lines: List[str] = []
    event_y0 = 196
    event_box_h = 30 + max(1, len(event_summary)) * 18
    for i, (event_id, event_label, count) in enumerate(event_summary):
        event_lines.append(
            f'<text x="{(tip_x + 14):.2f}" y="{(event_y0 + 22 + i * 18):.2f}" font-size="12" fill="#334155">{html.escape(event_label)}（{count}） · {html.escape(event_id)}</text>'
        )

    svg = f"""<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"{width}\" height=\"{height}\" viewBox=\"0 0 {width} {height}\">
  <rect x=\"0\" y=\"0\" width=\"{width}\" height=\"{height}\" fill=\"#FFFFFF\"/>
  <text x=\"{left_pad}\" y=\"36\" font-size=\"24\" font-family=\"Segoe UI, Arial, sans-serif\" fill=\"#1F2937\">{html.escape(source_name)}</text>
  <text x=\"{left_pad}\" y=\"58\" font-size=\"14\" font-family=\"Segoe UI, Arial, sans-serif\" fill=\"#4B5563\">场景：{html.escape(scenario_label)} ｜ 步数：{min_step}..{max_step}</text>
  <text x=\"{left_pad}\" y=\"78\" font-size=\"13\" font-family=\"Segoe UI, Arial, sans-serif\" fill=\"#6B7280\">说明：{html.escape(scenario_desc)}</text>
  <rect x=\"{tip_x}\" y=\"{tip_y}\" width=\"438\" height=\"{tip_box_h}\" fill=\"#F8FAFC\" stroke=\"#CBD5E1\" rx=\"8\" />
  {''.join(tip_lines)}
  <rect x=\"{tip_x}\" y=\"{event_y0}\" width=\"438\" height=\"{event_box_h}\" fill=\"#F9FAFB\" stroke=\"#E5E7EB\" rx=\"8\" />
  <text x=\"{tip_x + 14}\" y=\"{event_y0 + 16}\" font-size=\"12\" fill=\"#374151\">执行事件 Top4（按出现次数）</text>
  {''.join(event_lines)}
  <text x=\"{left_pad}\" y=\"{panel1_y - 12}\" font-size=\"14\" fill=\"#374151\">情绪面板：pleasure（绿） vs pressure=annoyed+jealous（红）</text>
  {p1_bg}
  <polyline fill=\"none\" stroke=\"#1B9E77\" stroke-width=\"2.2\" points=\"{p1_pleasure}\"/>
  <polyline fill=\"none\" stroke=\"#D94841\" stroke-width=\"2.0\" points=\"{p1_pressure}\"/>
  <text x=\"{left_pad}\" y=\"{panel2_y - 12}\" font-size=\"14\" fill=\"#374151\">关系面板：favor（蓝） vs bond（紫）</text>
  {p2_bg}
  <polyline fill=\"none\" stroke=\"#2563EB\" stroke-width=\"2.2\" points=\"{p2_favor}\"/>
  <polyline fill=\"none\" stroke=\"#7C3AED\" stroke-width=\"2.0\" points=\"{p2_bond}\"/>
  {''.join(legend_parts)}
  <text x=\"{width - 12}\" y=\"{height - 12}\" text-anchor=\"end\" font-size=\"11\" fill=\"#9CA3AF\">由 scripts/plot_emotion_curves.py 自动生成</text>
</svg>
"""
    out_path.write_text(svg, encoding="utf-8")


def summarize(rows: List[Dict[str, float | str]]) -> Dict[str, float]:
    pleasures = [float(r["pleasure"]) for r in rows]
    favors = [float(r["favor"]) for r in rows]
    bonds = [float(r["bond"]) for r in rows]
    pressures = [pressure_of(r) for r in rows]
    return {
        "steps": float(len(rows)),
        "pleasure_min": min(pleasures) if pleasures else 0.0,
        "pleasure_max": max(pleasures) if pleasures else 0.0,
        "favor_min": min(favors) if favors else 0.0,
        "favor_max": max(favors) if favors else 0.0,
        "bond_min": min(bonds) if bonds else 0.0,
        "bond_max": max(bonds) if bonds else 0.0,
        "pressure_min": min(pressures) if pressures else 0.0,
        "pressure_max": max(pressures) if pressures else 0.0,
    }


def build_event_stats(rows: List[Dict[str, float | str]]) -> List[Dict[str, object]]:
    stat: Dict[str, Dict[str, object]] = {}
    total = max(1, len(rows))
    for row in rows:
        event_id = str(row["event_id"])
        if event_id not in stat:
            stat[event_id] = {
                "event_id": event_id,
                "event_label": resolve_event_label(event_id),
                "count": 0,
                "intensity_sum": 0.0,
                "phases": set(),
            }
        item = stat[event_id]
        item["count"] = int(item["count"]) + 1
        item["intensity_sum"] = float(item["intensity_sum"]) + float(row["intensity"])
        item["phases"].add(str(row["phase"]))

    result: List[Dict[str, object]] = []
    for item in stat.values():
        count = int(item["count"])
        avg_intensity = float(item["intensity_sum"]) / max(1, count)
        phases = sorted(str(p) for p in item["phases"])
        result.append(
            {
                "event_id": item["event_id"],
                "event_label": item["event_label"],
                "count": count,
                "ratio": count / total,
                "avg_intensity": avg_intensity,
                "phases": phases,
            }
        )
    result.sort(key=lambda x: (-int(x["count"]), str(x["event_id"])))
    return result


def format_event_detail(row: Dict[str, float | str]) -> str:
    detail = str(row.get("event_detail", "")).strip()
    if detail:
        return detail
    phase = PHASE_LABELS.get(str(row["phase"]), str(row["phase"]))
    note = str(row.get("note", "")).strip()
    core = (
        f"阶段={phase}；强度={float(row['intensity']):.2f}；"
        f"社交竞争={int(row['rivals'])}；"
        f"他人亲密={float(row['last_other_affection']):.2f}；"
        f"他人均值好感={float(row['mean_other_favor']):.2f}"
    )
    if note:
        return core + f"；脚本反应={note}"
    return core


def pick_sample_rows(rows: List[Dict[str, float | str]], limit: int = 80) -> List[Dict[str, float | str]]:
    if not rows:
        return []

    picked: Dict[int, Dict[str, float | str]] = {}

    def add(row: Dict[str, float | str]) -> None:
        picked[int(row["step"])] = row

    add(rows[0])
    add(rows[-1])

    prev_phase = None
    for row in rows:
        phase = str(row["phase"])
        if phase != prev_phase:
            add(row)
            prev_phase = phase

    add(min(rows, key=lambda r: float(r["pleasure"])))
    add(max(rows, key=pressure_of))
    add(min(rows, key=lambda r: float(r["bond"])))
    add(min(rows, key=lambda r: float(r["favor"])))

    noted = [r for r in rows if str(r.get("note", "")).strip()]
    if len(noted) <= 50:
        for row in noted:
            add(row)
    else:
        for row in noted[:25]:
            add(row)
        for row in noted[-25:]:
            add(row)

    if len(picked) < 28:
        stride = max(1, len(rows) // 28)
        for i in range(0, len(rows), stride):
            add(rows[i])

    ordered = [picked[k] for k in sorted(picked)]
    if len(ordered) <= limit:
        return ordered

    stride = max(1, len(ordered) // limit)
    compact = ordered[::stride]
    if compact[-1] is not ordered[-1]:
        compact.append(ordered[-1])
    return compact[:limit]


def write_event_detail_page(
    output_dir: Path,
    source_name: str,
    chart_key: str,
    rows: List[Dict[str, float | str]],
    summary: Dict[str, float],
) -> Path:
    scenario_label = resolve_scenario_label(chart_key, str(rows[0]["scenario"]) if rows else chart_key)
    scenario_desc = resolve_scenario_desc(chart_key)

    event_stats = build_event_stats(rows)
    stats_rows: List[str] = []
    for item in event_stats:
        phases = " / ".join(PHASE_LABELS.get(str(p), str(p)) for p in item["phases"])
        stats_rows.append(
            "<tr>"
            f"<td>{html.escape(str(item['event_label']))}</td>"
            f"<td><code>{html.escape(str(item['event_id']))}</code></td>"
            f"<td>{int(item['count'])}</td>"
            f"<td>{float(item['ratio']) * 100:.1f}%</td>"
            f"<td>{float(item['avg_intensity']):.3f}</td>"
            f"<td>{html.escape(phases)}</td>"
            "</tr>"
        )

    sample_rows = pick_sample_rows(rows)
    sample_html_rows: List[str] = []
    for row in sample_rows:
        sample_html_rows.append(
            "<tr>"
            f"<td>{int(row['step'])}</td>"
            f"<td>{html.escape(PHASE_LABELS.get(str(row['phase']), str(row['phase'])))}</td>"
            f"<td>{html.escape(str(row['event_label']))}</td>"
            f"<td><code>{html.escape(str(row['event_id']))}</code></td>"
            f"<td>{float(row['intensity']):.3f}</td>"
            f"<td>{html.escape(format_event_detail(row))}</td>"
            f"<td>{float(row['pleasure']):.3f}</td>"
            f"<td>{pressure_of(row):.3f}</td>"
            f"<td>{float(row['favor']):.3f}</td>"
            f"<td>{float(row['bond']):.3f}</td>"
            f"<td>{html.escape(str(row['mood_top']))}</td>"
            "</tr>"
        )

    detail_name = chart_key + "_events.html"
    detail_path = output_dir / detail_name
    html_text = f"""<!doctype html>
<html lang=\"zh-CN\">
<head>
<meta charset=\"utf-8\" />
<title>{html.escape(source_name)} 事件详情</title>
<style>
body {{ font-family: Segoe UI, Arial, sans-serif; margin: 24px; color: #1f2937; }}
a {{ color: #2563eb; text-decoration: none; }}
a:hover {{ text-decoration: underline; }}
code {{ background: #f3f4f6; padding: 1px 4px; border-radius: 4px; }}
table {{ border-collapse: collapse; margin-top: 10px; min-width: 980px; }}
th, td {{ border: 1px solid #e5e7eb; padding: 7px 9px; font-size: 13px; vertical-align: top; }}
th {{ background: #f9fafb; text-align: left; }}
.tip {{ background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px 12px; margin-top: 12px; }}
</style>
</head>
<body>
<h1>{html.escape(source_name)} · 事件详情</h1>
<p><a href=\"index.html\">返回总览</a> ｜ <a href=\"{html.escape(chart_key + '.svg')}\">查看对应曲线图</a></p>
<p><strong>场景：</strong>{html.escape(scenario_label)}</p>
<p><strong>场景说明：</strong>{html.escape(scenario_desc)}</p>
<div class=\"tip\">
<p><strong>TIP：</strong>事件执行顺序决定情绪轨迹；同一事件在不同阶段会产生不同结果。</p>
<p><strong>TIP：</strong>数值统一在 0~1：<code>pleasure</code> 越高越积极，<code>pressure</code> 越高越紧张，<code>favor/bond</code> 分别对应长期好感与关系稳定。</p>
<p><strong>TIP：</strong>“事件详情”列优先显示脚本注释与台词；无脚本注释时显示自动推导的强度与社交上下文。</p>
</div>

<h2>摘要</h2>
<table>
<thead><tr><th>总步数</th><th>Pleasure范围</th><th>Pressure范围</th><th>Favor范围</th><th>Bond范围</th></tr></thead>
<tbody><tr>
<td>{int(summary['steps'])}</td>
<td>{summary['pleasure_min']:.3f}~{summary['pleasure_max']:.3f}</td>
<td>{summary['pressure_min']:.3f}~{summary['pressure_max']:.3f}</td>
<td>{summary['favor_min']:.3f}~{summary['favor_max']:.3f}</td>
<td>{summary['bond_min']:.3f}~{summary['bond_max']:.3f}</td>
</tr></tbody>
</table>

<h2>执行事件统计</h2>
<table>
<thead><tr><th>事件</th><th>事件ID</th><th>次数</th><th>占比</th><th>平均强度</th><th>主要阶段</th></tr></thead>
<tbody>
{''.join(stats_rows)}
</tbody>
</table>

<h2>关键执行样本</h2>
<p>包含阶段切换点、极值点、带台词/说明的关键步骤。</p>
<table>
<thead><tr><th>step</th><th>阶段</th><th>事件</th><th>事件ID</th><th>强度</th><th>事件详情</th><th>pleasure</th><th>pressure</th><th>favor</th><th>bond</th><th>mood_top</th></tr></thead>
<tbody>
{''.join(sample_html_rows)}
</tbody>
</table>
</body>
</html>
"""
    detail_path.write_text(html_text, encoding="utf-8")
    return detail_path


def write_index(output_dir: Path, rendered: List[Dict[str, object]]) -> None:
    rows: List[str] = []
    for item in rendered:
        name = str(item["svg_name"])
        chart_key = str(item["chart_key"])
        summary = item["summary"]
        scenario_label = str(item["scenario_label"])
        scenario_desc = str(item["scenario_desc"])
        event_detail_name = str(item["event_detail_name"])
        rows.append(
            "<tr>"
            f"<td><a href=\"{html.escape(name)}\">{html.escape(name)}</a></td>"
            f"<td>{html.escape(scenario_label)}</td>"
            f"<td>{html.escape(scenario_desc)}</td>"
            f"<td><a href=\"{html.escape(event_detail_name)}\">查看事件详情</a></td>"
            f"<td>{int(summary['steps'])}</td>"
            f"<td>{summary['pleasure_min']:.3f}~{summary['pleasure_max']:.3f}</td>"
            f"<td>{summary['pressure_min']:.3f}~{summary['pressure_max']:.3f}</td>"
            f"<td>{summary['favor_min']:.3f}~{summary['favor_max']:.3f}</td>"
            f"<td>{summary['bond_min']:.3f}~{summary['bond_max']:.3f}</td>"
            "</tr>"
        )

    html_text = f"""<!doctype html>
<html lang=\"zh-CN\">
<head>
<meta charset=\"utf-8\" />
<title>情绪曲线图总览</title>
<style>
body {{ font-family: Segoe UI, Arial, sans-serif; margin: 24px; color: #1f2937; }}
a {{ color: #2563eb; text-decoration: none; }}
a:hover {{ text-decoration: underline; }}
table {{ border-collapse: collapse; margin-top: 12px; min-width: 1360px; }}
th, td {{ border: 1px solid #e5e7eb; padding: 8px 10px; font-size: 14px; }}
th {{ background: #f9fafb; text-align: left; }}
</style>
</head>
<body>
<h1>情绪曲线图总览</h1>
<p>数据来源：<code>build/reports/emotion-curves/*.csv</code></p>
<p><strong>TIP：</strong>数值统一在 0~1。通常 <code>pleasure</code> 越高越积极，<code>pressure(annoyed+jealous)</code> 越高越紧张，<code>bond</code> 越高关系越稳。</p>
<p><strong>TIP：</strong>接近 0 常代表弱变化/低水平，接近 1 常代表强变化/高水平。请结合阶段色块与事件详情判断“为什么变化”。</p>
<table>
<thead><tr><th>图表</th><th>场景</th><th>场景说明</th><th>执行事件</th><th>步数</th><th>Pleasure范围</th><th>Pressure范围</th><th>Favor范围</th><th>Bond范围</th></tr></thead>
<tbody>
{''.join(rows)}
</tbody>
</table>
</body>
</html>
"""
    (output_dir / "index.html").write_text(html_text, encoding="utf-8")


def run(input_dir: Path, output_dir: Path) -> int:
    if not input_dir.exists():
        print(f"[plot] 输入目录不存在: {input_dir}")
        return 1

    output_dir.mkdir(parents=True, exist_ok=True)
    rendered: List[Dict[str, object]] = []

    csv_files = sorted(input_dir.glob("*.csv"))
    if not csv_files:
        print(f"[plot] 在目录中未找到 CSV: {input_dir}")
        return 1

    for csv_path in csv_files:
        rows = read_rows(csv_path)
        chart_key = csv_path.stem
        svg_name = chart_key + ".svg"
        svg_path = output_dir / svg_name

        render_svg(rows, csv_path.name, svg_path)
        summary = summarize(rows)
        detail_path = write_event_detail_page(output_dir, csv_path.name, chart_key, rows, summary)

        scenario_label = resolve_scenario_label(chart_key, str(rows[0]["scenario"]) if rows else chart_key)
        scenario_desc = resolve_scenario_desc(chart_key)
        rendered.append(
            {
                "svg_name": svg_name,
                "chart_key": chart_key,
                "summary": summary,
                "scenario_label": scenario_label,
                "scenario_desc": scenario_desc,
                "event_detail_name": detail_path.name,
            }
        )

        print(f"[plot] 已生成 {svg_path}（{int(summary['steps'])} 行）")
        print(f"[plot] 已生成 {detail_path}")

    write_index(output_dir, rendered)
    summary_json = {
        str(item["svg_name"]): {
            **item["summary"],
            "scenario_label": item["scenario_label"],
            "scenario_desc": item["scenario_desc"],
            "event_detail_page": item["event_detail_name"],
        }
        for item in rendered
    }
    (output_dir / "summary.json").write_text(json.dumps(summary_json, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"[plot] 已生成 {(output_dir / 'index.html')}")
    print(f"[plot] 已生成 {(output_dir / 'summary.json')}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="将情绪曲线 CSV 渲染为 SVG 图表与事件详情页。")
    parser.add_argument("--input", default="build/reports/emotion-curves", help="CSV 输入目录")
    parser.add_argument("--output", default="build/reports/emotion-charts", help="图表输出目录")
    args = parser.parse_args()
    return run(Path(args.input), Path(args.output))


if __name__ == "__main__":
    raise SystemExit(main())
