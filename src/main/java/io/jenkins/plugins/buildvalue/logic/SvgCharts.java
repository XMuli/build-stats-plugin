package io.jenkins.plugins.buildvalue.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SvgCharts {
    private SvgCharts() {}

    static final class Slice {
        final String label;
        final double value;
        final String color;
        final String detail;

        Slice(String label, double value, String color, String detail) {
            this.label = label;
            this.value = value;
            this.color = color;
            this.detail = detail == null ? "" : detail;
        }
    }

    static final class Bar {
        final String label;
        final String title;
        final double value;
        final String valueText;

        Bar(String label, String title, double value, String valueText) {
            this.label = label;
            this.title = title;
            this.value = value;
            this.valueText = valueText;
        }
    }

    static final class Series {
        final String name;
        final String color;
        final List<Double> values;

        Series(String name, String color, List<Double> values) {
            this.name = name;
            this.color = color;
            this.values = values;
        }
    }

    static String lines(String title, List<String> labels, List<Series> series, String unit) {
        double max = 0;
        int n = labels.size();
        for (Series seriesItem : series) {
            for (Double value : seriesItem.values) {
                if (value != null && value > max) {
                    max = value;
                }
            }
        }
        if (n == 0 || max <= 0) {
            return empty(title);
        }
        int width = 720;
        int height = 248;
        int left = 46;
        int right = 12;
        int top = 10;
        int bottom = 50;
        int innerW = width - left - right;
        int innerH = height - top - bottom;
        double yMax = nice(max);
        StringBuilder svg = start(width, height, title);
        grid(svg, left, top, innerW, innerH, yMax, unit);
        for (Series seriesItem : series) {
            StringBuilder path = new StringBuilder();
            boolean pen = false;
            int size = Math.min(n, seriesItem.values.size());
            for (int i = 0; i < size; i++) {
                Double value = seriesItem.values.get(i);
                if (value == null) {
                    pen = false;
                    continue;
                }
                double x = n == 1 ? left + innerW / 2.0 : left + (innerW * (double) i) / (n - 1);
                double y = top + innerH - (value / yMax) * innerH;
                path.append(pen ? 'L' : 'M').append(fmt(x)).append(' ').append(fmt(y));
                pen = true;
            }
            svg.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(seriesItem.color)
                    .append("\" stroke-width=\"1.6\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>");
        }
        xLabels(svg, labels, n, left, innerW, top + innerH + 16);
        legend(svg, series, 12, height - 14, width - 16);
        crosshair(svg);
        hoverBands(svg, labels, left, top, innerW, innerH, n, yMax, unit, series);
        svg.append("</svg>");
        return svg.toString();
    }

    static String stacked(String title, List<String> labels, List<Series> series, String unit) {
        int n = labels.size();
        double max = 0;
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (Series seriesItem : series) {
                if (i < seriesItem.values.size() && seriesItem.values.get(i) != null) {
                    sum += seriesItem.values.get(i);
                }
            }
            if (sum > max) {
                max = sum;
            }
        }
        if (n == 0 || max <= 0) {
            return empty(title);
        }
        int width = 720;
        int height = 248;
        int left = 44;
        int right = 12;
        int top = 10;
        int bottom = 50;
        int innerW = width - left - right;
        int innerH = height - top - bottom;
        double yMax = nice(max);
        StringBuilder svg = start(width, height, title);
        grid(svg, left, top, innerW, innerH, yMax, unit);
        double slot = innerW / (double) n;
        double barW = Math.max(1, slot * 0.78);
        for (int i = 0; i < n; i++) {
            double x = left + i * slot + (slot - barW) / 2;
            double base = top + innerH;
            double total = 0;
            StringBuilder tip = new StringBuilder(labels.get(i));
            for (Series seriesItem : series) {
                double value = i < seriesItem.values.size() && seriesItem.values.get(i) != null ? seriesItem.values.get(i) : 0;
                total += value;
                double h = (value / yMax) * innerH;
                if (h > 0) {
                    base -= h;
                    svg.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(base))
                            .append("\" width=\"").append(fmt(barW)).append("\" height=\"").append(fmt(h))
                            .append("\" fill=\"").append(seriesItem.color).append("\"/>");
                }
                tip.append("  ").append(seriesItem.name).append(' ').append(Texts.num(value)).append(unit);
            }
            tip.insert(labels.get(i).length(), "  全部 " + Texts.num(total) + unit);
            svg.append("<rect class=\"bv-hit\" data-tip=\"").append(Texts.escape(tip.toString()))
                    .append("\" x=\"").append(fmt(left + i * slot)).append("\" y=\"").append(top)
                    .append("\" width=\"").append(fmt(slot)).append("\" height=\"").append(innerH)
                    .append("\" fill=\"transparent\"></rect>");
        }
        xLabels(svg, labels, n, left, innerW, top + innerH + 16);
        legend(svg, series, 12, height - 14, width - 16);
        svg.append("</svg>");
        return svg.toString();
    }

    static String line(String title, List<String> labels, List<Double> values, String color, boolean fill, String unit) {
        int width = 720;
        int height = 236;
        int left = 46;
        int right = 12;
        int top = 10;
        int bottom = 46;
        int innerW = width - left - right;
        int innerH = height - top - bottom;
        double max = 0;
        int present = 0;
        for (Double value : values) {
            if (value != null) {
                present++;
                if (value > max) {
                    max = value;
                }
            }
        }
        if (present == 0) {
            return empty(title);
        }
        double yMax = "%".equals(unit) ? 100 : nice(max);
        StringBuilder svg = start(width, height, title);
        grid(svg, left, top, innerW, innerH, yMax, unit);
        int n = values.size();
        StringBuilder line = new StringBuilder();
        StringBuilder area = new StringBuilder();
        boolean pen = false;
        Double firstX = null;
        Double lastX = null;
        double base = top + innerH;
        for (int i = 0; i < n; i++) {
            Double value = values.get(i);
            double x = n == 1 ? left + innerW / 2.0 : left + (innerW * (double) i) / (n - 1);
            if (value == null) {
                pen = false;
                continue;
            }
            double y = top + innerH - (value / yMax) * innerH;
            if (!pen) {
                line.append("M");
                if (fill) {
                    area.append("M").append(fmt(x)).append(' ').append(fmt(base)).append('L');
                }
                pen = true;
                if (firstX == null) {
                    firstX = x;
                }
            } else {
                line.append('L');
                if (fill) {
                    area.append('L');
                }
            }
            line.append(fmt(x)).append(' ').append(fmt(y));
            if (fill) {
                area.append(fmt(x)).append(' ').append(fmt(y));
            }
            lastX = x;
        }
        if (fill && firstX != null && lastX != null) {
            area.append("L").append(fmt(lastX)).append(' ').append(fmt(base)).append('Z');
            svg.append("<path d=\"").append(area).append("\" fill=\"").append(color).append("\" opacity=\"0.22\"/>");
        }
        svg.append("<path d=\"").append(line).append("\" fill=\"none\" stroke=\"").append(color)
                .append("\" stroke-width=\"1.6\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>");
        xLabels(svg, labels, n, left, innerW, top + innerH + 16);
        legend(svg, List.of(new Series(title, color, List.of())), 12, height - 12, width - 16);
        crosshair(svg);
        List<Series> single = List.of(new Series("", color, values));
        hoverBands(svg, labels, left, top, innerW, innerH, n, yMax, unit, single);
        svg.append("</svg>");
        return svg.toString();
    }

    static String donut(String title, String center, String sub, List<Slice> slices) {
        double total = 0;
        List<Slice> drawn = new ArrayList<>();
        for (Slice slice : slices) {
            if (slice.value > 0) {
                drawn.add(slice);
                total += slice.value;
            }
        }
        if (total <= 0 || drawn.isEmpty()) {
            return empty(title);
        }
        int width = 720;
        int height = 268;
        double cx = width / 2.0;
        double cy = 78;
        double radius = 54;
        double circumference = 2 * Math.PI * radius;
        StringBuilder svg = start(width, height, title);
        svg.append("<g transform=\"rotate(-90 ").append(fmt(cx)).append(' ').append(fmt(cy)).append(")\">");
        double offset = 0;
        int gap = drawn.size() > 1 ? 2 : 0;
        List<String> tips = new ArrayList<>();
        List<String> colors = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<double[]> pointers = new ArrayList<>();
        StringBuilder hits = new StringBuilder();
        for (Slice slice : drawn) {
            double length = circumference * slice.value / total;
            double dash = Math.max(0, length - gap);
            svg.append("<circle cx=\"").append(fmt(cx)).append("\" cy=\"").append(fmt(cy))
                    .append("\" r=\"").append(fmt(radius)).append("\" fill=\"none\" stroke=\"")
                    .append(slice.color).append("\" stroke-width=\"18\" stroke-linecap=\"butt\" stroke-dasharray=\"")
                    .append(fmt(dash)).append(' ').append(fmt(circumference - dash))
                    .append("\" stroke-dashoffset=\"").append(fmt(-offset)).append("\" pointer-events=\"none\"/>");
            double a0 = offset / circumference * Math.PI * 2;
            double a1 = (offset + dash) / circumference * Math.PI * 2;
            double mid = (a0 + a1) / 2;
            String shareText = Texts.num(100.0 * slice.value / total) + "%";
            String tip = slice.label + "  " + (slice.detail.isEmpty() ? "" : slice.detail + "  ") + shareText;
            tips.add(tip);
            colors.add(slice.color);
            names.add(slice.label + " " + shareText);
            double rim = radius + 11;
            pointers.add(new double[] {
                    cx + rim * Math.sin(mid), cy - rim * Math.cos(mid),
                    cx + (rim + 18) * Math.sin(mid), cy - (rim + 18) * Math.cos(mid),
                    cx + rim * Math.sin(mid), cy - rim * Math.cos(mid)
            });
            hits.append("<path class=\"bv-hit\" fill=\"transparent\" d=\"").append(wedge(cx, cy, 40, 74, a0, a1)).append("\"")
                    .append(pointerAttrs(tip, slice.color, pointers.get(pointers.size() - 1)))
                    .append("/>");
            offset += length;
        }
        svg.append("</g>");
        svg.append(hits);
        svg.append("<text class=\"bv-pie-center\" pointer-events=\"none\" x=\"").append(fmt(cx)).append("\" y=\"").append(fmt(cy + 2))
                .append("\" text-anchor=\"middle\" font-size=\"22\" font-weight=\"600\" fill=\"#1c2b3a\">")
                .append(Texts.escape(center)).append("</text>");
        svg.append("<text pointer-events=\"none\" x=\"").append(fmt(cx)).append("\" y=\"").append(fmt(cy + 18))
                .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#8b97a3\">")
                .append(Texts.escape(sub)).append("</text>");
        int cursor = 12;
        int row = 214;
        int maxWidth = width - 16;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int need = 18 + name.length() * 12 + 12;
            if (cursor > 12 && cursor + need > 12 + maxWidth) {
                cursor = 12;
                row += 16;
            }
            svg.append("<rect pointer-events=\"none\" x=\"").append(cursor).append("\" y=\"").append(row - 8)
                    .append("\" width=\"8\" height=\"8\" fill=\"").append(colors.get(i)).append("\"/>");
            svg.append("<text pointer-events=\"none\" x=\"").append(cursor + 12).append("\" y=\"").append(row)
                    .append("\" font-size=\"11\" fill=\"#8b97a3\">").append(Texts.escape(name)).append("</text>");
            svg.append("<rect class=\"bv-hit\" fill=\"transparent\" x=\"").append(cursor - 2).append("\" y=\"").append(row - 12)
                    .append("\" width=\"").append(need).append("\" height=\"16\"")
                    .append(pointerAttrs(tips.get(i), colors.get(i), pointers.get(i)))
                    .append("/>");
            cursor += need;
        }
        crosshair(svg);
        svg.append("</svg>");
        return svg.toString();
    }

    private static String pointerAttrs(String tip, String color, double[] pointer) {
        return " data-tip=\"" + Texts.escape(tip)
                + "\" data-color=\"" + color
                + "\" data-x1=\"" + fmt(pointer[0]) + "\" data-y1=\"" + fmt(pointer[1])
                + "\" data-x2=\"" + fmt(pointer[2]) + "\" data-y2=\"" + fmt(pointer[3])
                + "\" data-dot=\"" + fmt(pointer[4]) + "|" + fmt(pointer[5]) + "|" + color + "\"";
    }

    private static String wedge(double cx, double cy, double r0, double r1, double a0, double a1) {
        double x0 = cx + r1 * Math.sin(a0);
        double y0 = cy - r1 * Math.cos(a0);
        double x1 = cx + r1 * Math.sin(a1);
        double y1 = cy - r1 * Math.cos(a1);
        double ix1 = cx + r0 * Math.sin(a1);
        double iy1 = cy - r0 * Math.cos(a1);
        double ix0 = cx + r0 * Math.sin(a0);
        double iy0 = cy - r0 * Math.cos(a0);
        int large = (a1 - a0) > Math.PI ? 1 : 0;
        return "M" + fmt(x0) + " " + fmt(y0)
                + "A" + fmt(r1) + " " + fmt(r1) + " 0 " + large + " 1 " + fmt(x1) + " " + fmt(y1)
                + "L" + fmt(ix1) + " " + fmt(iy1)
                + "A" + fmt(r0) + " " + fmt(r0) + " 0 " + large + " 0 " + fmt(ix0) + " " + fmt(iy0)
                + "Z";
    }

    static String columns(String title, List<String> labels, List<Double> values, String color) {
        int n = Math.min(labels.size(), values.size());
        if (n == 0) {
            return empty(title);
        }
        double max = 0;
        for (int i = 0; i < n; i++) {
            if (values.get(i) != null && values.get(i) > max) {
                max = values.get(i);
            }
        }
        if (max <= 0) {
            return empty(title);
        }
        double yMax = nice(max);
        int width = 720;
        int height = 236;
        int left = 44;
        int right = 12;
        int top = 10;
        int bottom = 46;
        int innerW = width - left - right;
        int innerH = height - top - bottom;
        StringBuilder svg = start(width, height, title);
        grid(svg, left, top, innerW, innerH, yMax, "");
        double slot = innerW / (double) n;
        double barW = Math.max(2, slot * 0.72);
        for (int i = 0; i < n; i++) {
            double value = values.get(i) == null ? 0 : values.get(i);
            double h = (value / yMax) * innerH;
            double x = left + i * slot + (slot - barW) / 2;
            double y = top + innerH - h;
            svg.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                    .append("\" width=\"").append(fmt(barW)).append("\" height=\"").append(fmt(Math.max(h, value > 0 ? 1 : 0)))
                    .append("\" fill=\"").append(color).append("\"/>");
        }
        int step = n > 16 ? 3 : 1;
        for (int i = 0; i < n; i += step) {
            double x = left + i * slot + slot / 2;
            svg.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(top + innerH + 16)
                    .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#8b97a3\">")
                    .append(Texts.escape(labels.get(i))).append("</text>");
        }
        legend(svg, List.of(new Series("次数", color, List.of())), 12, height - 12, width - 16);
        for (int i = 0; i < n; i++) {
            double value = values.get(i) == null ? 0 : values.get(i);
            String tip = labels.get(i) + "  " + Texts.num(value);
            svg.append("<rect class=\"bv-hit\" data-tip=\"").append(Texts.escape(tip))
                    .append("\" x=\"").append(fmt(left + i * slot)).append("\" y=\"").append(top)
                    .append("\" width=\"").append(fmt(slot)).append("\" height=\"").append(innerH)
                    .append("\" fill=\"transparent\"></rect>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    static String bars(String title, List<Bar> bars) {
        if (bars.isEmpty()) {
            return empty(title);
        }
        int width = 720;
        int row = 32;
        int height = 12 + bars.size() * row;
        double max = 0;
        for (Bar bar : bars) {
            if (bar.value > max) {
                max = bar.value;
            }
        }
        if (max <= 0) {
            max = 1;
        }
        StringBuilder svg = start(width, height, title);
        int y = 8;
        for (Bar bar : bars) {
            int barX = 210;
            int barW = 360;
            int w = (int) Math.round(barW * (bar.value / max));
            svg.append("<text x=\"0\" y=\"").append(y + 16).append("\" font-size=\"12\" fill=\"currentColor\">")
                    .append(Texts.escape(Texts.clip(bar.label, 24))).append("</text>");
            svg.append("<rect x=\"").append(barX).append("\" y=\"").append(y + 6)
                    .append("\" width=\"").append(barW).append("\" height=\"8\" fill=\"#e8eef3\"/>");
            svg.append("<rect x=\"").append(barX).append("\" y=\"").append(y + 6)
                    .append("\" width=\"").append(Math.max(w, bar.value > 0 ? 2 : 0))
                    .append("\" height=\"8\" fill=\"#5dade2\"/>");
            svg.append("<text x=\"").append(barX + barW + 8).append("\" y=\"").append(y + 16)
                    .append("\" font-size=\"12\" fill=\"currentColor\">")
                    .append(Texts.escape(bar.valueText)).append("</text>");
            y += row;
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private static void crosshair(StringBuilder svg) {
        svg.append("<g class=\"bv-cross\" visibility=\"hidden\" pointer-events=\"none\">");
        svg.append("<line class=\"bv-cross-line\" stroke=\"#8b97a3\" stroke-opacity=\"0.9\" stroke-width=\"1\"/>");
        svg.append("<g class=\"bv-cross-dots\"></g></g>");
    }

    private static void hoverBands(
            StringBuilder svg,
            List<String> labels,
            int left,
            int top,
            int innerW,
            int innerH,
            int n,
            double yMax,
            String unit,
            List<Series> series) {
        double step = n <= 1 ? innerW : innerW / (double) (n - 1);
        double safeMax = yMax <= 0 ? 1 : yMax;
        for (int i = 0; i < n; i++) {
            double x = n == 1 ? left + innerW / 2.0 : left + i * step;
            double width = n <= 1 ? innerW : step;
            double x0 = Math.max(left, x - width / 2);
            double band = Math.min(width, left + innerW - x0);
            StringBuilder tip = new StringBuilder(i < labels.size() ? labels.get(i) : "");
            StringBuilder points = new StringBuilder();
            for (Series seriesItem : series) {
                Double raw = i < seriesItem.values.size() ? seriesItem.values.get(i) : null;
                tip.append("  ");
                if (!seriesItem.name.isEmpty()) {
                    tip.append(seriesItem.name).append(' ');
                }
                tip.append(raw == null ? "—" : Texts.num(raw) + unit);
                if (raw != null) {
                    double cy = top + innerH - (raw / safeMax) * innerH;
                    if (points.length() > 0) {
                        points.append(';');
                    }
                    points.append(fmt(cy)).append('|').append(seriesItem.color);
                }
            }
            String text = tip.toString();
            svg.append("<rect class=\"bv-hit\" data-tip=\"").append(Texts.escape(text))
                    .append("\" data-x=\"").append(fmt(x))
                    .append("\" data-top=\"").append(top)
                    .append("\" data-bottom=\"").append(top + innerH)
                    .append("\" data-ys=\"").append(Texts.escape(points.toString()))
                    .append("\" x=\"").append(fmt(x0)).append("\" y=\"").append(top)
                    .append("\" width=\"").append(fmt(band)).append("\" height=\"").append(innerH)
                    .append("\" fill=\"transparent\"></rect>");
        }
    }

    private static void legend(StringBuilder svg, List<Series> series, int x, int y, int maxWidth) {
        int cursor = x;
        int row = y;
        for (Series seriesItem : series) {
            if (seriesItem.name == null || seriesItem.name.isEmpty()) {
                continue;
            }
            int need = 18 + seriesItem.name.length() * 12 + 12;
            if (cursor > x && cursor + need > x + maxWidth) {
                cursor = x;
                row += 16;
            }
            svg.append("<rect x=\"").append(cursor).append("\" y=\"").append(row - 8)
                    .append("\" width=\"8\" height=\"8\" fill=\"").append(seriesItem.color).append("\"/>");
            svg.append("<text x=\"").append(cursor + 12).append("\" y=\"").append(row)
                    .append("\" font-size=\"11\" fill=\"#8b97a3\">")
                    .append(Texts.escape(seriesItem.name)).append("</text>");
            cursor += need;
        }
    }

    private static void grid(StringBuilder svg, int left, int top, int innerW, int innerH, double yMax, String unit) {
        for (int tick = 0; tick <= 4; tick++) {
            double value = yMax * tick / 4.0;
            double y = top + innerH - (innerH * tick / 4.0);
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(fmt(y))
                    .append("\" x2=\"").append(left + innerW).append("\" y2=\"").append(fmt(y))
                    .append("\" stroke=\"#e8edf2\"/>");
            svg.append("<text x=\"").append(left - 8).append("\" y=\"").append(fmt(y + 3))
                    .append("\" text-anchor=\"end\" font-size=\"11\" fill=\"#8b97a3\">")
                    .append(Texts.escape(Texts.num(value))).append(Texts.escape(unit)).append("</text>");
        }
    }

    private static void xLabels(StringBuilder svg, List<String> labels, int n, int left, int innerW, int y) {
        if (n == 0) {
            return;
        }
        int step = Math.max(1, (int) Math.ceil(n / 6.0));
        for (int i = 0; i < n; i += step) {
            double x = n == 1 ? left + innerW / 2.0 : left + (innerW * (double) i) / (n - 1);
            svg.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(y)
                    .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#8b97a3\">")
                    .append(Texts.escape(labels.get(i))).append("</text>");
        }
        if ((n - 1) % step != 0) {
            double x = left + innerW;
            svg.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(y)
                    .append("\" text-anchor=\"middle\" font-size=\"11\" fill=\"#8b97a3\">")
                    .append(Texts.escape(labels.get(n - 1))).append("</text>");
        }
    }

    static String stageFlow(String buildLabel, List<StageSample> stages) {
        if (stages == null || stages.isEmpty()) {
            return empty("最近一次阶段");
        }
        int perRow = 6;
        int nodeW = 112;
        int gapX = 36;
        int rowH = 78;
        int left = 20;
        int top = 28;
        int rows = (stages.size() + perRow - 1) / perRow;
        int width = left + perRow * (nodeW + gapX);
        int height = top + rows * rowH + 8;
        StringBuilder svg = start(width, height, buildLabel);
        svg.append("<text x=\"").append(left).append("\" y=\"16\" font-size=\"12\" fill=\"#5c6b7a\">")
                .append(Texts.escape(buildLabel)).append("</text>");
        for (int i = 0; i < stages.size(); i++) {
            StageSample stage = stages.get(i);
            int col = i % perRow;
            int row = i / perRow;
            int x = left + col * (nodeW + gapX);
            int y = top + row * rowH;
            if (col > 0) {
                svg.append("<line x1=\"").append(x - gapX).append("\" y1=\"").append(y + 16)
                        .append("\" x2=\"").append(x).append("\" y2=\"").append(y + 16)
                        .append("\" stroke=\"#c5ced6\" stroke-width=\"1.5\"/>");
            }
            String color = stage.failed() ? "#F08048" : "SUCCESS".equals(stage.status) ? "#50A040" : "#9DA0A5";
            String mark = stage.failed() ? "×" : "SUCCESS".equals(stage.status) ? "✓" : "·";
            String tip = stage.name + "  " + stage.status + "  " + Texts.duration(stage.durationMs);
            svg.append("<rect class=\"bv-hit\" rx=\"16\" x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(nodeW).append("\" height=\"32\" fill=\"#fff\" stroke=\"")
                    .append(color).append("\" stroke-width=\"1.5\" data-tip=\"").append(Texts.escape(tip)).append("\"/>");
            svg.append("<text pointer-events=\"none\" x=\"").append(x + 10).append("\" y=\"").append(y + 21)
                    .append("\" font-size=\"12\" fill=\"").append(color).append("\">").append(mark).append("</text>");
            svg.append("<text pointer-events=\"none\" x=\"").append(x + 26).append("\" y=\"").append(y + 21)
                    .append("\" font-size=\"11\" fill=\"#1c2b3a\">")
                    .append(Texts.escape(Texts.clip(stage.name, 10))).append("</text>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    static String emptyPublic(String title) {
        return empty(title);
    }

    static String statusDots(String title, List<String> labels, List<String> stages, List<List<String>> cells) {
        int cols = labels.size();
        int rows = stages.size();
        if (cols == 0 || rows == 0) {
            return empty(title);
        }
        int textWidth = 0;
        for (String name : stages) {
            int widthOfName = 0;
            for (int i = 0; i < name.length(); i++) {
                widthOfName += name.charAt(i) <= 127 ? 7 : 12;
            }
            textWidth = Math.max(textWidth, widthOfName);
        }
        int left = textWidth + 12;
        int top = 4;
        int cellH = 12;
        int pitch = 15;
        int cellW = Math.max(6, Math.min(12, 640 / Math.max(1, cols)));
        int width = left + cols * cellW + 8;
        int height = top + rows * pitch + 20;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"bv-status\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height).append("\" role=\"img\">");
        for (int r = 0; r < rows; r++) {
            int y = top + r * pitch;
            svg.append("<text x=\"0\" y=\"").append(y + 10).append("\" font-size=\"11\" fill=\"#5c6b7a\">")
                    .append(Texts.escape(stages.get(r))).append("</text>");
            for (int c = 0; c < cols; c++) {
                String status = c < cells.get(r).size() ? cells.get(r).get(c) : "";
                String color = statusColor(status);
                int x = left + c * cellW;
                String tip = labels.get(c) + "  " + stages.get(r) + "  " + (status.isEmpty() ? "无" : status);
                svg.append("<rect class=\"bv-hit\" data-tip=\"").append(Texts.escape(tip)).append("\" x=\"").append(x)
                        .append("\" y=\"").append(y).append("\" width=\"").append(cellW - 2).append("\" height=\"").append(cellH)
                        .append("\" fill=\"").append(color).append("\"/>");
            }
        }
        svg.append("<rect x=\"").append(left).append("\" y=\"").append(height - 16).append("\" width=\"8\" height=\"8\" fill=\"#50A040\"/>");
        svg.append("<text x=\"").append(left + 12).append("\" y=\"").append(height - 8).append("\" font-size=\"11\" fill=\"#8b97a3\">成功</text>");
        svg.append("<rect x=\"").append(left + 52).append("\" y=\"").append(height - 16).append("\" width=\"8\" height=\"8\" fill=\"#F08048\"/>");
        svg.append("<text x=\"").append(left + 64).append("\" y=\"").append(height - 8).append("\" font-size=\"11\" fill=\"#8b97a3\">失败</text>");
        svg.append("<rect x=\"").append(left + 104).append("\" y=\"").append(height - 16).append("\" width=\"8\" height=\"8\" fill=\"#9DA0A5\"/>");
        svg.append("<text x=\"").append(left + 116).append("\" y=\"").append(height - 8).append("\" font-size=\"11\" fill=\"#8b97a3\">其它</text>");
        svg.append("</svg>");
        return svg.toString();
    }

    private static String statusColor(String status) {
        if ("SUCCESS".equals(status)) {
            return "#50A040";
        }
        if ("FAILED".equals(status) || "FAILURE".equals(status)) {
            return "#F08048";
        }
        if (status == null || status.isEmpty()) {
            return "#eef2f5";
        }
        return "#9DA0A5";
    }

    private static String empty(String title) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 720 80\" role=\"img\"><text x=\"0\" y=\"36\" font-size=\"13\" fill=\"#9DA0A5\">无数据</text></svg>";
    }

    private static StringBuilder start(int width, int height, String title) {
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ')
                .append(height).append("\" role=\"img\">");
        return svg;
    }

    private static double nice(double max) {
        if (max <= 0) {
            return 1;
        }
        double exp = Math.pow(10, Math.floor(Math.log10(max)));
        double fraction = max / exp;
        double niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
        return niceFraction * exp;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
