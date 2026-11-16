/* 拾股 mockup renderer — deterministic, no external deps */

(function () {
  "use strict";

  function mulberry32(a) {
    return function () {
      a |= 0;
      a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function svgEl(name, attrs) {
    const el = document.createElementNS("http://www.w3.org/2000/svg", name);
    if (attrs) Object.keys(attrs).forEach((k) => el.setAttribute(k, attrs[k]));
    return el;
  }

  /* ---------------- sparklines ---------------- */

  function drawSpark(el) {
    const seed = parseInt(el.dataset.seed || "1", 10);
    const trend = el.dataset.trend;
    const rnd = mulberry32(seed * 999 + 17);
    const W = 46;
    const H = 20;
    const n = 22;
    const up = trend ? trend === "up" : seed % 2 === 0;
    const pts = [];
    let value = 0;
    for (let i = 0; i < n; i++) {
      const drift = up ? 0.16 : -0.12;
      value += drift + (rnd() - 0.5) * 1.1;
      pts.push(value);
    }
    const lo = Math.min.apply(null, pts);
    const hi = Math.max.apply(null, pts);
    const span = hi - lo || 1;
    const pad = 2.4;
    const coords = pts.map((v, i) => {
      const x = pad + ((W - pad * 2) * i) / (n - 1);
      const y = pad + (H - pad * 2) * (1 - (v - lo) / span);
      return [x, y];
    });
    const svg = svgEl("svg", { viewBox: "0 0 " + W + " " + H, width: W, height: H });
    let d = "";
    coords.forEach((p, i) => {
      d += (i === 0 ? "M" : "L") + p[0].toFixed(1) + " " + p[1].toFixed(1) + " ";
    });
    svg.appendChild(svgEl("path", {
      d: d,
      fill: "none",
      stroke: "#23201C",
      "stroke-width": "1.3",
      "stroke-linecap": "round",
      "stroke-linejoin": "round",
      opacity: "0.82",
    }));
    const last = coords[coords.length - 1];
    svg.appendChild(svgEl("circle", {
      cx: last[0], cy: last[1], r: "1.6", fill: "#23201C",
    }));
    el.replaceWith(svg);
  }

  /* ---------------- K line ---------------- */

  function buildCandles(seed, count, targetLast) {
    const rnd = mulberry32(seed);
    const raw = [];
    let p = 100;
    for (let i = 0; i < count; i++) {
      const cyc = Math.sin(i / 6.2) * 0.0022 + Math.sin(i / 15.5) * 0.0018;
      const drift = 0.0016 + cyc;
      const open = p;
      const chg = (rnd() - 0.48) * 0.021 + drift;
      const close = open * (1 + chg);
      const high = Math.max(open, close) * (1 + rnd() * 0.0075);
      const low = Math.min(open, close) * (1 - rnd() * 0.0075);
      raw.push({ open, close, high, low, vol: 0.25 + rnd() * 0.75 });
      p = close;
    }
    const factor = targetLast / raw[raw.length - 1].close;
    return raw.map((c) => ({
      open: c.open * factor,
      close: c.close * factor,
      high: c.high * factor,
      low: c.low * factor,
      vol: c.vol,
    }));
  }

  function drawKline() {
    const host = document.getElementById("kline");
    if (!host) return;
    const candles = buildCandles(20261116, 48, 43.15);
    const W = 306;
    const H = 130;
    const right = 38;
    const left = 2;
    const top = 7;
    const priceBottom = 92;
    const volTop = 98;
    const volBottom = 121;
    const plotW = W - left - right;

    let lo = Infinity;
    let hi = -Infinity;
    candles.forEach((c) => {
      lo = Math.min(lo, c.low);
      hi = Math.max(hi, c.high);
    });
    const span = hi - lo || 1;
    const y = (v) => top + (priceBottom - top) * (1 - (v - lo) / span);

    const svg = svgEl("svg", { viewBox: "0 0 " + W + " " + H, width: W, height: H });

    // faint horizontal guides
    [0.25, 0.5, 0.75].forEach((f) => {
      const yy = top + (priceBottom - top) * f;
      svg.appendChild(svgEl("line", {
        x1: left, x2: W - right, y1: yy, y2: yy,
        stroke: "#B9B0A3", "stroke-width": "0.7", opacity: "0.45",
        "stroke-dasharray": "1.5 4",
      }));
    });

    const g = svgEl("g", { filter: "url(#wob-soft)" });
    const step = plotW / candles.length;
    const bodyW = Math.max(3.2, step * 0.55);

    candles.forEach((c, i) => {
      const cx = left + step * (i + 0.5);
      const up = c.close >= c.open;
      const color = up ? "#D8453A" : "#2E7D5B";
      g.appendChild(svgEl("line", {
        x1: cx, x2: cx,
        y1: y(c.high), y2: y(c.low),
        stroke: color, "stroke-width": "0.9", opacity: "0.9",
      }));
      const yo = y(c.open);
      const yc = y(c.close);
      const bh = Math.max(1.1, Math.abs(yc - yo));
      g.appendChild(svgEl("rect", {
        x: cx - bodyW / 2,
        y: Math.min(yo, yc),
        width: bodyW,
        height: bh,
        fill: up ? "#FBF8F2" : color,
        stroke: color,
        "stroke-width": "1.05",
      }));
    });

    // volume
    const maxVol = Math.max.apply(null, candles.map((c) => c.vol));
    candles.forEach((c, i) => {
      const cx = left + step * (i + 0.5);
      const up = c.close >= c.open;
      const h = (volBottom - volTop) * (c.vol / maxVol);
      g.appendChild(svgEl("rect", {
        x: cx - bodyW / 2,
        y: volBottom - h,
        width: bodyW,
        height: h,
        fill: up ? "#D8453A" : "#2E7D5B",
        opacity: "0.32",
      }));
    });

    // MA20
    const ma = [];
    candles.forEach((c, i) => {
      if (i < 19) return;
      let sum = 0;
      for (let k = i - 19; k <= i; k++) sum += candles[k].close;
      ma.push([left + step * (i + 0.5), y(sum / 20)]);
    });
    let md = "";
    ma.forEach((p, i) => {
      md += (i === 0 ? "M" : "L") + p[0].toFixed(1) + " " + p[1].toFixed(1) + " ";
    });
    g.appendChild(svgEl("path", {
      d: md, fill: "none", stroke: "#23201C", "stroke-width": "1.15",
      "stroke-linecap": "round", opacity: "0.62",
    }));
    svg.appendChild(g);

    // last close line
    const last = candles[candles.length - 1];
    const ly = y(last.close);
    svg.appendChild(svgEl("line", {
      x1: left, x2: W - right, y1: ly, y2: ly,
      stroke: "#23201C", "stroke-width": "0.8", "stroke-dasharray": "3 3", opacity: "0.5",
    }));

    // price labels (right gutter)
    const labels = [
      { v: hi, yy: top + 3 },
      { v: (hi + lo) / 2, yy: (top + priceBottom) / 2 + 2 },
      { v: lo, yy: priceBottom - 1 },
    ];
    labels.forEach((l) => {
      const t = svgEl("text", {
        x: W - 3, y: l.yy, "text-anchor": "end",
        "font-size": "7.2", fill: "#6E675E",
        "font-family": "\"PingFang SC\",\"Microsoft YaHei\",sans-serif",
      });
      t.textContent = l.v.toFixed(2);
      svg.appendChild(t);
    });

    // last price tag
    const tagW = 32;
    const tagH = 12;
    const tagX = W - tagW - 1;
    const tagY = Math.min(H - tagH - 2, Math.max(1, ly - tagH / 2));
    svg.appendChild(svgEl("rect", {
      x: tagX, y: tagY, width: tagW, height: tagH, rx: "2.4",
      fill: "#FBF8F2", stroke: "#23201C", "stroke-width": "0.9",
      transform: "rotate(-1 " + (tagX + tagW / 2) + " " + (tagY + tagH / 2) + ")",
    }));
    const tagText = svgEl("text", {
      x: tagX + tagW / 2, y: tagY + 8.4, "text-anchor": "middle",
      "font-size": "7.4", "font-weight": "600", fill: "#D8453A",
      "font-family": "\"PingFang SC\",\"Microsoft YaHei\",sans-serif",
    });
    tagText.textContent = "43.15";
    svg.appendChild(tagText);

    // date labels
    const dates = ["09-01", "10-09", "11-13"];
    dates.forEach((d, i) => {
      const t = svgEl("text", {
        x: i === 0 ? left : i === 1 ? left + plotW / 2 : W - right,
        y: H - 1,
        "text-anchor": i === 0 ? "start" : i === 1 ? "middle" : "end",
        "font-size": "7", fill: "#B9B0A3",
        "font-family": "\"PingFang SC\",\"Microsoft YaHei\",sans-serif",
      });
      t.textContent = d;
      svg.appendChild(t);
    });

    host.appendChild(svg);
  }

  /* ---------------- review chart ---------------- */

  function drawReviewChart() {
    const host = document.getElementById("review-chart");
    if (!host) return;
    const W = 306;
    const H = 100;
    const left = 3;
    const right = 3;
    const top = 5;
    const bottom = 9;
    const rnd = mulberry32(7719);
    const n = 61;
    const a = [];
    const b = [];
    let va = 0;
    let vb = 0;
    for (let i = 0; i < n; i++) {
      const cyc = Math.sin(i / 9) * 0.0018;
      va += 0.00115 + cyc + (rnd() - 0.5) * 0.0058;
      vb += 0.00038 + cyc * 0.7 + (rnd() - 0.5) * 0.0044;
      a.push(va);
      b.push(vb);
    }
    // normalize ends to intended values
    const endA = 0.068;
    const endB = 0.022;
    const scaleA = endA / a[n - 1];
    const scaleB = endB / b[n - 1];
    for (let i = 0; i < n; i++) {
      a[i] *= scaleA;
      b[i] *= scaleB;
    }
    const all = a.concat(b).concat([0]);
    const lo = Math.min.apply(null, all);
    const hi = Math.max.apply(null, all);
    const span = hi - lo || 1;
    const x = (i) => left + ((W - left - right) * i) / (n - 1);
    const y = (v) => top + (H - top - bottom) * (1 - (v - lo) / span);

    const svg = svgEl("svg", { viewBox: "0 0 " + W + " " + H, width: W, height: H });

    // zero baseline
    svg.appendChild(svgEl("line", {
      x1: left, x2: W - right, y1: y(0), y2: y(0),
      stroke: "#B9B0A3", "stroke-width": "0.8", "stroke-dasharray": "2 4",
    }));

    // portfolio area
    let area = "M" + x(0).toFixed(1) + " " + y(0).toFixed(1) + " ";
    for (let i = 0; i < n; i++) area += "L" + x(i).toFixed(1) + " " + y(a[i]).toFixed(1) + " ";
    area += "L" + x(n - 1).toFixed(1) + " " + y(0).toFixed(1) + " Z";
    svg.appendChild(svgEl("path", { d: area, fill: "#23201C", opacity: "0.055", stroke: "none" }));

    function line(values, color, width, dash) {
      let d = "";
      for (let i = 0; i < n; i++) {
        d += (i === 0 ? "M" : "L") + x(i).toFixed(1) + " " + y(values[i]).toFixed(1) + " ";
      }
      svg.appendChild(svgEl("path", {
        d: d, fill: "none", stroke: color, "stroke-width": width,
        "stroke-linecap": "round", "stroke-linejoin": "round",
        "stroke-dasharray": dash || "",
        filter: "url(#wob-soft)",
      }));
    }
    line(b, "#B9B0A3", "1.15", "3 3");
    line(a, "#23201C", "1.7");

    // end dots
    [[a, "#23201C"], [b, "#B9B0A3"]].forEach((pair) => {
      const values = pair[0];
      svg.appendChild(svgEl("circle", {
        cx: x(n - 1), cy: y(values[n - 1]), r: "2.4",
        fill: "#FBF8F2", stroke: pair[1], "stroke-width": "1.3",
      }));
    });

    host.appendChild(svg);
  }

  /* ---------------- solo mode ---------------- */

  function soloMode() {
    const only = new URLSearchParams(window.location.search).get("only");
    if (!only) return;
    document.body.classList.add("solo");
    document.querySelectorAll(".screen-set").forEach((el) => {
      if (el.dataset.screen !== only) el.remove();
    });
    document.title = "拾股 · " + only;
  }

  function init() {
    document.querySelectorAll(".spark").forEach(drawSpark);
    drawKline();
    drawReviewChart();
    soloMode();
    window.__shiguReady = true;
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
