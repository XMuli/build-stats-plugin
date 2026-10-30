package io.jenkins.plugins.buildvalue.logic;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

public final class PageHtml {
    private PageHtml() {}

    public static String body(Dashboard dashboard) {
        return body(dashboard, "");
    }

    public static String body(Dashboard dashboard, String scope) {
        return body(dashboard, scope, "");
    }

    public static String body(Dashboard dashboard, String scope, String host) {
        String tab = scope.isEmpty() ? "bv-tab" : "bv-" + scope + "-tab";
        String radio = scope.isEmpty() ? "bv-tab" : "bv-tab-" + scope;
        StringBuilder html = new StringBuilder(12_000);
        html.append("<div class=\"bv\" data-logo=\"").append(pluginAsset("images/logo.jpg")).append("\">");
        if (dashboard.empty) {
            html.append("<p class=\"bv-empty\">无构建记录。</p>");
            note(html, dashboard);
            html.append("</div>");
            return html.toString();
        }

        html.append("<div class=\"bv-tabs\">");
        html.append("<input type=\"radio\" name=\"").append(radio).append("\" id=\"").append(tab).append("-all\" checked>");
        html.append("<input type=\"radio\" name=\"").append(radio).append("\" id=\"").append(tab).append("-host\">");
        html.append("<input type=\"radio\" name=\"").append(radio).append("\" id=\"").append(tab).append("-about\">");
        for (int i = 0; i < dashboard.tabs.size(); i++) {
            html.append("<input type=\"radio\" name=\"").append(radio).append("\" id=\"").append(tab).append("-").append(i).append("\">");
        }
        html.append("<style>");
        html.append(".bv-tabs:has(#").append(tab).append("-all:checked) .bv-panel-all{display:block}");
        html.append(".bv-tabs:has(#").append(tab).append("-all:checked) label[for=\"").append(tab).append("-all\"]{background:var(--accent-color,#3ca2ea);color:var(--text-color-on-accent,#fff)}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) .bv-panel-host{display:block}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) label[for=\"").append(tab).append("-host\"]{background:var(--accent-color,#3ca2ea);color:var(--text-color-on-accent,#fff)}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) .bv-lead,.bv-tabs:has(#").append(tab).append("-host:checked) .bv-banner,.bv-tabs:has(#").append(tab).append("-host:checked) .bv-note{display:none}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) .bv-home-sign{display:none}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) .bv-panel-about{display:block}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) label[for=\"").append(tab).append("-about\"]{background:var(--accent-color,#3ca2ea);color:var(--text-color-on-accent,#fff)}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) .bv-lead,.bv-tabs:has(#").append(tab).append("-about:checked) .bv-banner{display:none}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) .bv-home-sign{display:none}");
        for (int i = 0; i < dashboard.tabs.size(); i++) {
            html.append(".bv-tabs:has(#").append(tab).append("-").append(i).append(":checked) .bv-panel-").append(i).append("{display:block}");
            html.append(".bv-tabs:has(#").append(tab).append("-").append(i).append(":checked) label[for=\"").append(tab).append("-").append(i)
                    .append("\"]{background:var(--accent-color,#3ca2ea);color:var(--text-color-on-accent,#fff)}");
        }
        html.append("</style><div class=\"bv-shell\"><aside class=\"bv-side\"><div class=\"bv-side-brand\" data-i18n=\"nav.brand\">构建统计</div><div class=\"bv-tabbar\"><label for=\"")
                .append(tab).append("-all\" data-i18n=\"nav.overview\">总览</label><label for=\"").append(tab).append("-host\" data-i18n=\"nav.host\">本机</label></div><div class=\"bv-side-label\" data-i18n=\"nav.pipes\">流水线</div><div class=\"bv-tabbar\">");
        for (int i = 0; i < dashboard.tabs.size(); i++) {
            html.append("<label for=\"").append(tab).append("-").append(i).append("\">")
                    .append(Texts.escape(dashboard.tabs.get(i).jobName)).append("</label>");
        }
        html.append("</div><div class=\"bv-side-foot\"><label for=\"").append(tab).append("-about\" data-i18n=\"nav.about\">关于</label></div></aside><div class=\"bv-main\"><header class=\"bv-nav\"><span class=\"bv-crumb\" data-i18n=\"nav.brand\">构建统计</span>");
        if (!host.isEmpty()) {
            html.append("<span class=\"bv-range-label\" data-i18n=\"nav.machine\">机器</span>");
        }
        html.append("<span class=\"bv-range-label\" data-i18n=\"nav.time\">时间</span><nav class=\"bv-ranges\">");
        range(html, host, dashboard.days, 7, "近 7 天");
        range(html, host, dashboard.days, 30, "近 30 天");
        range(html, host, dashboard.days, 90, "近 90 天");
        range(html, host, dashboard.days, 0, "全部");
        html.append("</nav><label class=\"bv-sign-switch\"><input type=\"checkbox\" checked><span data-i18n=\"sign.toggle\">状态牌</span></label></header>");
        if (dashboard.banner != null && !dashboard.banner.isEmpty()) {
            html.append("<p class=\"bv-banner\">").append(Texts.escape(dashboard.banner)).append("</p>");
        }
        html.append("<p class=\"bv-lead\">").append(Texts.escape(dashboard.headline)).append("</p>");

        html.append("<div class=\"bv-panel bv-panel-all\"><div class=\"bv-page-head\">");
        heat(html, dashboard.heatJson);
        sign(html, dashboard.signImage, dashboard.signAlt, dashboard.signCaption, dashboard.signLive);
        html.append("</div><div class=\"bv-board\">");
        Dashboard.Kpi kpi = dashboard.kpi;
        chart(html, "累计机器小时", dashboard.hoursChart);
        chart(html, "累计成功、失败和中止", dashboard.cumSplitChart);
        chart(html, "构建次数", dashboard.deliveryChart);
        chart(html, "构建时间段", dashboard.hourChart);
        chart(html, "成功率走势", dashboard.rateChart);
        chart(html, "结果", dashboard.resultPie);
        chart(html, "机器时间", dashboard.timePie);
        chart(html, "触发方式", dashboard.triggerPie);
        html.append("<article class=\"bv-tile bv-score\"><h3>成功率</h3><strong>")
                .append(Texts.escape(kpi.success)).append("</strong><ul class=\"bv-score-list\">");
        scoreRow(html, "成功次数", dashboard.deliveryText);
        scoreRow(html, "执行耗时", kpi.duration);
        scoreRow(html, "触发次数", kpi.builds);
        scoreRow(html, "失败和中止耗时", dashboard.wasteMachine);
        html.append("</ul><p class=\"bv-score-note\">").append(Texts.escape(dashboard.wasteLine)).append("</p></article>");

        html.append("<article class=\"bv-tile bv-span-3\"><h3>触发账号</h3>");
        peopleTable(html, dashboard.people, true);
        html.append("</article><article class=\"bv-tile bv-span-3\"><h3>各流水线</h3><p class=\"bv-muted\">")
                .append(Texts.escape(dashboard.otherSummary)).append("</p>");
        if (!dashboard.others.isEmpty()) {
            html.append("<div class=\"bv-table-card\"><table class=\"bv-table\"><thead><tr>");
            html.append("<th>流水线</th><th class=\"bv-num\">次数</th><th class=\"bv-num\">总耗时</th><th class=\"bv-num\">平均</th>");
            html.append("<th class=\"bv-num\">成功率</th><th class=\"bv-num\">失败</th><th>触发账号</th></tr></thead><tbody>");
            for (Dashboard.JobRow row : dashboard.others) {
                html.append("<tr><td>");
                link(html, row.href, row.name);
                html.append("</td><td class=\"bv-num\">").append(row.builds).append("</td><td class=\"bv-num\">")
                        .append(Texts.escape(row.duration)).append("</td><td class=\"bv-num\">")
                        .append(Texts.escape(row.average)).append("</td><td class=\"bv-num\">")
                        .append(Texts.escape(row.success)).append("</td><td class=\"bv-num\">")
                        .append(row.failures).append("</td><td>")
                        .append(Texts.escape(row.topPerson)).append("</td></tr>");
            }
            html.append("</tbody></table></div>");
        }
        html.append("</article><article class=\"bv-tile bv-span-3\"><h3>累计时间明细</h3>");
        html.append("<div class=\"bv-table-card bv-table-scroll\"><table class=\"bv-table\"><thead><tr>");
        html.append("<th>时间</th><th class=\"bv-num\">本期耗时</th><th class=\"bv-num\">累计耗时</th>");
        html.append("<th class=\"bv-num\">累计成功</th><th class=\"bv-num\">累计失败和中止</th></tr></thead><tbody>");
        for (Dashboard.CumRow row : dashboard.cumRows) {
            html.append("<tr><td>").append(Texts.escape(row.label)).append("</td><td class=\"bv-num\">")
                    .append(Texts.escape(row.period)).append("</td><td class=\"bv-num\">")
                    .append(Texts.escape(row.total)).append("</td><td class=\"bv-num\">")
                    .append(Texts.escape(row.success)).append("</td><td class=\"bv-num\">")
                    .append(Texts.escape(row.waste)).append("</td></tr>");
        }
        html.append("</tbody></table></div></article></div>");
        note(html, dashboard);
        html.append("</div>");

        for (int i = 0; i < dashboard.tabs.size(); i++) {
            html.append("<div class=\"bv-panel bv-panel-").append(i).append("\">");
            renderPanel(html, dashboard.tabs.get(i));
            html.append("</div>");
        }
        hostPanel(html);
        aboutPanel(html);
        html.append("</div></div></div>");
        html.append("<div class=\"bv-float\" hidden></div>");
        html.append("<script src=\"").append(pluginAsset("js/build-stats.js")).append("\"></script>");
        html.append("</div>");
        return html.toString();
    }

    private static void renderPanel(StringBuilder html, Dashboard.JobPanel panel) {
        html.append("<section class=\"bv-section bv-section-first\"><h2><i class=\"bv-mark ").append(panel.mark).append("\"></i>");
        link(html, panel.jobUrl, panel.jobName);
        html.append("</h2><p class=\"bv-muted\">").append(Texts.escape(panel.compare)).append("</p>");
        html.append("<div class=\"bv-page-head bv-job-head\"><section class=\"bv-kpis\">");
        card(html, "执行耗时", panel.machineTime, "", "");
        card(html, "触发次数", panel.builds, panel.people.size() + " 个账号", "");
        card(html, "成功率", panel.success, "平均 " + panel.average, "");
        card(html, "失败次数", panel.failures, "", "");
        html.append("</section>");
        sign(html, panel.signImage, panel.signAlt, panel.signCaption, panel.signLive);
        html.append("</div></section>");
        html.append("<div class=\"bv-board\">");
        chart(html, "耗时趋势", panel.durationChart);
        chart(html, "构建次数", panel.dailyCountsChart);
        chart(html, "构建时间段", panel.hourChart);
        chart(html, "成功率走势", panel.rateChart);
        if (panel.hasStages) {
            chartCaption(html, "阶段状态", panel.stageDots,
                    "横轴是最近 36 次构建，从左到右。纵轴是阶段名。绿是成功，橙是失败，灰是其它。停在格子上可看编号和状态。",
                    false);
            chartCaption(html, "阶段名称变化", panel.scriptChart,
                    "每一根柱是一周。柱高是这一周里，和上一次构建相比，阶段名称有增加或删除的次数。没有柱或写着无数据，表示这段时间阶段名称没有变。",
                    false);
            chart(html, "阶段平均耗时", panel.stageBars);
            chart(html, "失败落在哪个阶段", panel.failPie);
        }
        html.append("</div>");
        html.append("<div class=\"bv-pair\"><section><h2>触发账号</h2>");
        peopleTable(html, panel.people, false);
        html.append("</section><section><h2>最近的构建</h2>");
        if (panel.recent.isEmpty()) {
            html.append("<p class=\"bv-muted\">无记录。</p>");
        } else {
            html.append("<div class=\"bv-card bv-table-card\"><table class=\"bv-table\"><thead><tr>");
            html.append("<th>构建</th><th>结果</th><th class=\"bv-num\">耗时</th><th>触发人</th><th>开始</th>");
            html.append("</tr></thead><tbody>");
            for (Dashboard.BuildRow row : panel.recent) {
                html.append("<tr><td>");
                link(html, row.href, row.label);
                html.append("</td><td><span class=\"bv-badge ").append(row.resultClass).append("\">")
                        .append(Texts.escape(row.result)).append("</span></td><td class=\"bv-num\">")
                        .append(Texts.escape(row.duration)).append("</td><td>")
                        .append(Texts.escape(row.actor)).append("</td><td>")
                        .append(Texts.escape(row.when)).append("</td></tr>");
            }
            html.append("</tbody></table></div>");
        }
        html.append("</section></div>");
    }

    private static String peopleBars(java.util.List<Dashboard.PersonRow> people) {
        java.util.List<SvgCharts.Bar> bars = new java.util.ArrayList<>();
        int limit = Math.min(people.size(), 12);
        for (int i = 0; i < limit; i++) {
            Dashboard.PersonRow person = people.get(i);
            bars.add(new SvgCharts.Bar(person.name, person.name + " 触发 " + person.builds + " 次", person.builds,
                    person.builds + " 次"));
        }
        return SvgCharts.bars("触发次数", bars);
    }

    private static void peopleTable(StringBuilder html, java.util.List<Dashboard.PersonRow> people, boolean withJobs) {
        if (people.isEmpty()) {
            html.append("<p class=\"bv-muted\">无记录。</p>");
            return;
        }
        html.append("<div class=\"bv-card bv-table-card\"><table class=\"bv-table\"><thead><tr>");
        html.append("<th>账号</th><th class=\"bv-num\">次数</th><th class=\"bv-num\">成功</th><th class=\"bv-num\">失败</th>");
        html.append("<th class=\"bv-num\">中止</th><th class=\"bv-num\">成功率</th><th class=\"bv-num\">执行耗时</th><th>最近</th>");
        if (withJobs) {
            html.append("<th>流水线</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Dashboard.PersonRow row : people) {
            html.append("<tr><td>").append(Texts.escape(row.name)).append("</td><td class=\"bv-num\">")
                    .append(row.builds).append("</td><td class=\"bv-num\">").append(row.success)
                    .append("</td><td class=\"bv-num\">").append(row.failure).append("</td><td class=\"bv-num\">")
                    .append(row.aborted).append("</td><td class=\"bv-num\">").append(Texts.escape(row.successRate))
                    .append("</td><td class=\"bv-num\">").append(Texts.escape(row.duration)).append("</td><td>")
                    .append(Texts.escape(row.lastWhen)).append("</td>");
            if (withJobs) {
                html.append("<td>").append(Texts.escape(row.jobs)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
    }

    public static String homeMarkup(StatusSign sign) {
        if (sign == null || sign.image == null || sign.image.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"bv-home-sign\" data-bv-home>");
        html.append("<div class=\"bv-home-card\"><div class=\"bv-home-tools\">");
        html.append("<button type=\"button\" class=\"bv-home-peek\" aria-label=\"收到右侧\">⟩</button>");
        html.append("<button type=\"button\" class=\"bv-home-close\" aria-label=\"关闭\">×</button>");
        html.append("</div><figure class=\"bv-sign\"><a class=\"bv-home-picture\" href=\"")
                .append(contextPath()).append("/build-stats\">");
        if (sign.live) {
            html.append("<span class=\"bv-live-badge\">LIVE</span>");
        }
        html.append("<img src=\"").append(Texts.escape(pluginAsset("images/" + sign.image)))
                .append("\" alt=\"").append(Texts.escape(sign.alt)).append("\"></a>");
        int split = sign.caption == null ? -1 : sign.caption.indexOf('\n');
        String subject = split < 0 ? sign.caption : sign.caption.substring(0, split);
        String state = split < 0 ? "" : sign.caption.substring(split + 1);
        String caption = "<figcaption><strong>" + Texts.escape(subject) + "</strong>";
        if (!state.isEmpty()) {
            caption += "<span class=\"bv-state " + stateClass(state) + "\">" + Texts.escape(state) + "</span>";
        }
        caption += "</figcaption>";
        if (sign.buildUrl != null && !sign.buildUrl.isBlank()) {
            html.append("<a class=\"bv-home-build\" href=\"").append(Texts.escape(sign.buildUrl)).append("\">");
            html.append(caption).append("</a>");
        } else {
            html.append(caption);
        }
        html.append("</figure></div>");
        html.append("<button type=\"button\" class=\"bv-home-reopen\">统计</button></div>");
        html.append("<div class=\"bv-entry-guide\" hidden><div class=\"bv-entry-arrow\"></div>");
        html.append("<p class=\"bv-entry-text\"></p><div class=\"bv-entry-actions\">");
        html.append("<button type=\"button\" class=\"bv-entry-show\" hidden></button>");
        html.append("<button type=\"button\" class=\"bv-entry-ok\"></button></div></div>");
        html.append("<script src=\"").append(pluginAsset("js/home-card.js")).append("?v=4\"></script>");
        return html.toString();
    }

    private static void sign(StringBuilder html, String image, String alt, String caption, boolean live) {
        if (image == null || image.isEmpty()) {
            return;
        }
        html.append("<figure class=\"bv-sign\">");
        if (live) {
            html.append("<span class=\"bv-live-badge\">LIVE</span>");
        }
        int split = caption == null ? -1 : caption.indexOf('\n');
        String subject = split < 0 ? caption : caption.substring(0, split);
        String state = split < 0 ? "" : caption.substring(split + 1);
        html.append("<img src=\"").append(Texts.escape(pluginAsset("images/" + image)))
                .append("\" alt=\"").append(Texts.escape(alt)).append("\"><figcaption><strong>")
                .append(Texts.escape(subject)).append("</strong>");
        if (!state.isEmpty()) {
            html.append("<span class=\"bv-state ").append(stateClass(state)).append("\" data-i18n=\"")
                    .append(stateKey(state)).append("\">")
                    .append(Texts.escape(state)).append("</span>");
        }
        html.append("</figcaption></figure>");
    }

    private static String stateClass(String state) {
        return switch (state) {
            case "构建中" -> "is-run";
            case "成功" -> "is-ok";
            case "失败", "不稳定" -> "is-bad";
            case "中止", "未构建" -> "is-stop";
            default -> "";
        };
    }

    private static void heat(StringBuilder html, String json) {
        html.append("<section class=\"bv-heat\"><div class=\"bv-heat-row\">");
        html.append("<div class=\"bv-heat-plot\"></div>");
        html.append("<aside class=\"bv-heat-side\"><div class=\"bv-heat-side-top\">");
        html.append("<span class=\"bv-heat-size\"><button type=\"button\" data-size=\"s\" data-i18n=\"heat.s\">小</button><button type=\"button\" data-size=\"m\" data-i18n=\"heat.m\">中</button><button type=\"button\" data-size=\"l\" class=\"is-on\" data-i18n=\"heat.l\">大</button></span>");
        html.append("<div class=\"bv-heat-stats\"></div></div>");
        html.append("<label class=\"bv-heat-year-label\"><span data-i18n=\"heat.year\">年份</span><select class=\"bv-heat-year\"></select></label>");
        html.append("</aside></div>");
        html.append("<div class=\"bv-heat-data\" hidden>").append(json == null ? "{}" : json).append("</div></section>");
    }

    private static void card(StringBuilder html, String label, String value, String delta, String deltaClass) {
        html.append("<article class=\"bv-kpi\"><span").append(i18nAttr(label)).append(">").append(Texts.escape(label)).append("</span><strong>")
                .append(Texts.escape(value)).append("</strong>");
        if (delta != null && !delta.isEmpty()) {
            html.append("<em class=\"").append(deltaClass).append("\">").append(Texts.escape(delta)).append("</em>");
        }
        html.append("</article>");
    }

    private static void chartCaption(StringBuilder html, String title, String svg, String caption, boolean wide) {
        html.append("<article class=\"bv-card");
        if (wide) {
            html.append(" bv-span-3");
        }
        html.append("\"><h3").append(i18nAttr(title)).append(">").append(Texts.escape(title)).append("</h3><p class=\"bv-chart-note\">")
                .append(Texts.escape(caption)).append("</p>");
        html.append(svg);
        html.append("</article>");
    }

    private static void chart(StringBuilder html, String title, String svg) {
        html.append("<article class=\"bv-card").append(svg.contains("bv-wide") ? " bv-scroll" : "").append("\"><h3").append(i18nAttr(title)).append(">")
                .append(Texts.escape(title)).append("</h3>");
        html.append(svg);
        html.append("</article>");
    }

    private static void range(StringBuilder html, String host, int current, int days, String label) {
        html.append("<a");
        if (current == days) {
            html.append(" class=\"is-on\"");
        }
        html.append(" href=\"?");
        if (!host.isEmpty()) {
            html.append("host=").append(host).append("&");
        }
        html.append("days=").append(days).append("\"").append(i18nAttr(label)).append(">").append(label).append("</a>");
    }

    private static void link(StringBuilder html, String href, String label) {
        if (href == null || href.isEmpty()) {
            html.append(Texts.escape(label));
            return;
        }
        html.append("<a href=\"").append(Texts.escape(href)).append("\">").append(Texts.escape(label)).append("</a>");
    }

    private static void scoreRow(StringBuilder html, String label, String value) {
        html.append("<li><span").append(i18nAttr(label)).append(">").append(Texts.escape(label)).append("</span><b>")
                .append(Texts.escape(value)).append("</b></li>");
    }

    private static void hostPanel(StringBuilder html) {
        html.append("<div class=\"bv-panel bv-panel-host\">");
        html.append("<div class=\"bv-pace\"><span data-i18n=\"host.pace\">刷新</span>");
        html.append("<button type=\"button\" data-pace=\"1\" class=\"is-on\">1 秒</button>");
        html.append("<button type=\"button\" data-pace=\"5\">5 秒</button></div>");
        html.append("<div class=\"bv-board bv-host-board\">");
        liveCard(html, "cpu", "CPU", "占用", "#3479B0");
        liveCard(html, "mem", "内存", "已用 / 总共", "#3C74A3");
        liveCard(html, "disk", "磁盘", "活动时间", "#F08048");
        html.append("<div class=\"bv-gpu-slot\"></div>");
        liveCard(html, "down", "下载", "每秒", "#3479B0");
        liveCard(html, "up", "上传", "每秒", "#A2C7E4");
        liveCard(html, "read", "磁盘读", "每秒", "#3C74A3");
        liveCard(html, "write", "磁盘写", "每秒", "#F08048");
        html.append("</div>");
        html.append("<article class=\"bv-tile bv-host-jenkins\"><h3 data-i18n=\"host.now\">Jenkins 当前</h3><div class=\"bv-jenkins\"></div></article>");
        html.append("<p class=\"bv-live-status\">采样中</p></div>");
    }

    private static void liveCard(StringBuilder html, String key, String title, String sub, String color) {
        html.append("<article class=\"bv-tile bv-live\" data-live=\"").append(key).append("\">");
        if ("cpu".equals(key)) {
            html.append("<div class=\"bv-card-top\"><h3").append(i18nAttr(title)).append(">").append(title).append("</h3>");
            html.append("<div class=\"bv-core-switch\"><button type=\"button\" data-cpu=\"total\" class=\"is-on\" data-i18n=\"host.total\">总占用</button>");
            html.append("<button type=\"button\" data-cpu=\"cores\" data-i18n=\"host.cores\">各核心</button></div></div>");
            html.append("<p class=\"bv-cpu-meta\">型号读取中</p>");
            html.append("<svg class=\"bv-spark bv-cpu-total\" viewBox=\"0 0 320 150\" preserveAspectRatio=\"none\"></svg>");
            html.append("<div class=\"bv-core-grid\"></div>");
        } else {
            html.append("<h3").append(i18nAttr(title)).append(">").append(title).append("</h3>");
            html.append("<svg class=\"bv-spark\" viewBox=\"0 0 320 150\" preserveAspectRatio=\"none\"></svg>");
        }
        html.append("<div class=\"bv-series\"><i style=\"background:").append(color).append("\"></i><span class=\"bv-live-sub\">")
                .append(sub).append("</span><b class=\"bv-live-value\">--</b></div>");
        if ("disk".equals(key)) {
            html.append("<div class=\"bv-disk-list\"></div>");
        }
        html.append("</article>");
    }


    private static void aboutPanel(StringBuilder html) {
        html.append("<div class=\"bv-panel bv-panel-about\"><section class=\"bv-about\">");
        html.append("<div class=\"bv-about-hero\"><div class=\"bv-about-logo\">");
        html.append("<img class=\"bv-about-img\" alt=\"Logo\" src=\"")
                .append(pluginAsset("images/logo.jpg")).append("\"></div>");
        html.append("<div class=\"bv-about-id\"><p class=\"bv-about-kicker\" data-i18n=\"nav.about\">关于</p>");
        html.append("<h2 data-i18n=\"nav.brand\">构建统计</h2><p class=\"bv-about-by\">XMuli</p>");
        html.append("<div class=\"bv-about-actions\"><label class=\"bv-about-upload\"><span data-i18n=\"about.upload\">上传 Logo</span><input class=\"bv-logo-file\" type=\"file\" accept=\"image/png,image/jpeg,image/webp,image/svg+xml,image/gif\"></label>");
        html.append("<button type=\"button\" class=\"bv-logo-clear\" data-i18n=\"about.restore\">恢复默认</button></div></div></div>");
        html.append("<div class=\"bv-star\"><span class=\"bv-star-mark\">★</span>");
        html.append("<p data-i18n=\"about.star\">如果构建统计帮助了你，请在 GitHub 点个 Star，这对我们意义重大</p>");
        html.append("<a class=\"bv-star-go\" href=\"https://github.com/XMuli/build-stats-plugin\" target=\"_blank\" rel=\"noopener noreferrer\"><span data-i18n=\"about.starGo\">去点 Star</span></a>");
        html.append("<button type=\"button\" class=\"bv-star-close\" aria-label=\"关闭\">×</button></div>");
        html.append("<div class=\"bv-about-meta\">");
        html.append("<div class=\"bv-about-row\"><span data-i18n=\"about.lang\">语言</span><select class=\"bv-lang\"><option value=\"zh\">中文</option><option value=\"en\">English</option></select></div>");
        html.append("<div class=\"bv-about-row\"><span data-i18n=\"about.author\">作者</span><strong>XMuli</strong></div>");
        html.append("<div class=\"bv-about-row\"><span data-i18n=\"about.repo\">GitHub</span><a href=\"https://github.com/XMuli/build-stats-plugin\" target=\"_blank\" rel=\"noopener noreferrer\">github.com/XMuli/build-stats-plugin</a></div>");
        html.append("</div></section></div>");
    }

    private static String i18nAttr(String zh) {
        String key = i18nKey(zh);
        return key.isEmpty() ? "" : " data-i18n=\"" + key + "\"";
    }

    private static String stateKey(String state) {
        return switch (state) {
            case "构建中" -> "state.run";
            case "成功" -> "state.ok";
            case "失败" -> "state.fail";
            case "不稳定" -> "state.unstable";
            case "中止" -> "state.abort";
            case "未构建" -> "state.none";
            default -> "";
        };
    }

    private static String i18nKey(String zh) {
        return switch (zh) {
            case "构建统计" -> "nav.brand";
            case "总览" -> "nav.overview";
            case "本机" -> "nav.host";
            case "关于" -> "nav.about";
            case "流水线" -> "nav.pipes";
            case "机器" -> "nav.machine";
            case "时间" -> "nav.time";
            case "近 7 天" -> "range.7";
            case "近 30 天" -> "range.30";
            case "近 90 天" -> "range.90";
            case "全部" -> "range.all";
            case "累计机器小时" -> "chart.hours";
            case "累计成功、失败和中止" -> "chart.split";
            case "构建次数" -> "chart.count";
            case "构建时间段" -> "chart.hour";
            case "成功率走势" -> "chart.rate";
            case "结果" -> "chart.result";
            case "机器时间" -> "chart.mtime";
            case "触发方式" -> "chart.trigger";
            case "耗时趋势" -> "chart.trend";
            case "阶段状态" -> "chart.stage";
            case "阶段名称变化" -> "chart.names";
            case "阶段平均耗时" -> "chart.stageTime";
            case "失败落在哪个阶段" -> "chart.failStage";
            case "成功率" -> "kpi.rate";
            case "成功次数" -> "kpi.ok";
            case "执行耗时" -> "kpi.time";
            case "触发次数" -> "kpi.count";
            case "失败和中止耗时" -> "kpi.failTime";
            case "失败次数" -> "kpi.fail";
            case "CPU" -> "host.cpu";
            case "内存" -> "host.mem";
            case "磁盘" -> "host.disk";
            case "下载" -> "host.down";
            case "上传" -> "host.up";
            case "磁盘读" -> "host.read";
            case "磁盘写" -> "host.write";
            default -> "";
        };
    }


    private static void note(StringBuilder html, Dashboard dashboard) {
        html.append("<footer class=\"bv-note\"><strong>统计</strong><p>")
                .append(Texts.escape(dashboard.methodNote)).append("</p></footer>");
    }

    private static String contextPath() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null || request.getContextPath() == null) {
            return "";
        }
        return request.getContextPath();
    }

    private static String pluginAsset(String path) {
        return contextPath() + "/plugin/build-stats/" + path;
    }
}
