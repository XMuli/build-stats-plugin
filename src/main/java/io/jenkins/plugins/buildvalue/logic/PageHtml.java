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
        html.append("<div class=\"bv\">");
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
        html.append(".bv-tabs:has(#").append(tab).append("-all:checked) label[for=\"").append(tab).append("-all\"]{background:#3CA2EA;color:#fff}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) .bv-panel-host{display:block}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) label[for=\"").append(tab).append("-host\"]{background:#3CA2EA;color:#fff}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) .bv-lead,.bv-tabs:has(#").append(tab).append("-host:checked) .bv-banner,.bv-tabs:has(#").append(tab).append("-host:checked) .bv-note{display:none}");
        html.append(".bv-tabs:has(#").append(tab).append("-host:checked) .bv-home-sign{display:none}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) .bv-panel-about{display:block}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) label[for=\"").append(tab).append("-about\"]{background:#3CA2EA;color:#fff}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) .bv-lead,.bv-tabs:has(#").append(tab).append("-about:checked) .bv-banner{display:none}");
        html.append(".bv-tabs:has(#").append(tab).append("-about:checked) .bv-home-sign{display:none}");
        for (int i = 0; i < dashboard.tabs.size(); i++) {
            html.append(".bv-tabs:has(#").append(tab).append("-").append(i).append(":checked) .bv-panel-").append(i).append("{display:block}");
            html.append(".bv-tabs:has(#").append(tab).append("-").append(i).append(":checked) label[for=\"").append(tab).append("-").append(i)
                    .append("\"]{background:#3CA2EA;color:#fff}");
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
        html.append("<div class=\"bv-float\" hidden></div><script>");
        html.append("document.addEventListener('mousemove',function(e){");
        html.append("var tip=document.body.querySelector(':scope > .bv-float');");
        html.append("if(!tip){tip=document.createElement('div');tip.className='bv-float';tip.hidden=true;document.body.appendChild(tip);}");
        html.append("var node=e.target.closest?e.target.closest('[data-tip]'):null;");
        html.append("document.querySelectorAll('.bv-cross').forEach(function(g){g.setAttribute('visibility','hidden');});");
        html.append("if(!node||(node.closest('.bv-range')&&node.closest('.bv-range').hidden)){tip.hidden=true;return;}");
        html.append("var node=e.target.closest?e.target.closest('[data-tip]'):null;");
        html.append("document.querySelectorAll('.bv-cross').forEach(function(g){g.setAttribute('visibility','hidden');});");
        html.append("if(!node){tip.hidden=true;return;}");
        html.append("tip.hidden=false;tip.textContent=node.getAttribute('data-tip');");
        html.append("tip.style.left=(e.clientX+14)+'px';tip.style.top=(e.clientY+16)+'px';");
        html.append("var svg=node.ownerSVGElement;if(!svg)return;");
        html.append("var cross=svg.querySelector('.bv-cross');if(!cross)return;");
        html.append("var line=cross.querySelector('.bv-cross-line');");
        html.append("var dots=cross.querySelector('.bv-cross-dots');while(dots.firstChild)dots.removeChild(dots.firstChild);");
        html.append("var ns='http://www.w3.org/2000/svg';");
        html.append("if(node.getAttribute('data-x1')){");
        html.append("var pt=svg.createSVGPoint();pt.x=e.clientX;pt.y=e.clientY;");
        html.append("var ctm=svg.getScreenCTM();var local=ctm?pt.matrixTransform(ctm.inverse()):null;");
        html.append("line.setAttribute('x1',node.getAttribute('data-x1'));line.setAttribute('y1',node.getAttribute('data-y1'));");
        html.append("line.setAttribute('x2',local?local.x:node.getAttribute('data-x2'));");
        html.append("line.setAttribute('y2',local?local.y:node.getAttribute('data-y2'));");
        html.append("line.setAttribute('stroke',node.getAttribute('data-color')||'#8b97a3');");
        html.append("var dot=(node.getAttribute('data-dot')||'').split('|');");
        html.append("if(dot.length>=3){var c=document.createElementNS(ns,'circle');");
        html.append("c.setAttribute('cx',dot[0]);c.setAttribute('cy',dot[1]);c.setAttribute('r','3.5');");
        html.append("c.setAttribute('fill',dot[2]);c.setAttribute('stroke','none');");
        html.append("dots.appendChild(c);}");
        html.append("tip.style.left=(e.clientX+14)+'px';tip.style.top=(e.clientY+16)+'px';");
        html.append("cross.setAttribute('visibility','visible');return;}");
        html.append("if(!node.getAttribute('data-x'))return;");
        html.append("line.setAttribute('stroke','#8b97a3');");
        html.append("var x=node.getAttribute('data-x');");
        html.append("line.setAttribute('x1',x);line.setAttribute('x2',x);");
        html.append("line.setAttribute('y1',node.getAttribute('data-top'));line.setAttribute('y2',node.getAttribute('data-bottom'));");
        html.append("(node.getAttribute('data-ys')||'').split(';').forEach(function(item){");
        html.append("if(!item)return;var p=item.split('|');var c=document.createElementNS(ns,'circle');");
        html.append("c.setAttribute('cx',x);c.setAttribute('cy',p[0]);c.setAttribute('r','4.5');");
        html.append("c.setAttribute('fill','#fff');c.setAttribute('stroke',p[1]||'#3479B0');c.setAttribute('stroke-width','2');");
        html.append("dots.appendChild(c);});cross.setAttribute('visibility','visible');});");
        html.append("</script>");
        heatScript(html);
        langScript(html);
        liveScript(html);
        signScript(html);
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
        html.append("<script type=\"application/json\" class=\"bv-heat-data\">").append(json == null ? "{}" : json).append("</script></section>");
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

    private static void heatScript(StringBuilder html) {
        html.append("<script>");
        html.append("if(!window.__bvHeat){window.__bvHeat=true;");
        html.append("var colors=['#ebedf0','#c6e48b','#7bc96f','#239a3b','#196127'];");
        html.append("function parseDay(s){var p=s.split('-');return new Date(+p[0],+p[1]-1,+p[2]);}");
        html.append("function dayKey(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}");
        html.append("function align(d,startDow){var x=new Date(d.getTime());var delta=(x.getDay()-startDow+7)%7;x.setDate(x.getDate()-delta);return x;}");
        html.append("function level(n,max){if(!n)return 0;if(max<=1)return 4;return Math.min(4,Math.max(1,Math.ceil(n/max*4)));}");
        html.append("function workAverage(data,dates){var first=parseDay(dates[0]);var today=new Date();today.setHours(0,0,0,0);var days=0,sum=0;");
        html.append("for(var d=new Date(first.getTime());d<=today;d.setDate(d.getDate()+1)){var dow=d.getDay();if(dow===0||dow===6)continue;days++;sum+=data[dayKey(d)]||0;}");
        html.append("return days?(sum/days).toFixed(2):'0';}");
        html.append("function fit(root){var plot=root.querySelector('.bv-heat-plot');var grid=plot&&plot.querySelector('.bv-heat-grid');if(!grid||!grid.children.length)return;");
        html.append("var cols=grid.children.length;var head=root.closest('.bv-page-head')||root;var width=head.clientWidth;if(width<80)return;");
        html.append("var side=root.querySelector('.bv-heat-side');var sideH=side?side.offsetHeight:0;var sign=head.querySelector('.bv-sign');");
        html.append("var signH=sign&&sign.offsetHeight>40?sign.offsetHeight:0;var innerW=Math.max(80,width-240);");
        html.append("var innerH=signH>40?signH-sideH-36:0;var cellW=Math.floor((innerW-22-(cols-1)*3)/cols);");
        html.append("var cellH=innerH>28?Math.floor((innerH-18)/7):cellW;var cell=Math.min(cellW,cellH,18);");
        html.append("var scale=root.__size==='s'?0.72:root.__size==='m'?0.86:1;cell=Math.max(4,Math.floor(cell*scale));");
        html.append("if(plot.__cell===cell)return;plot.__cell=cell;plot.style.setProperty('--cell',cell+'px');}");
        html.append("function paint(root){var data=root.__data;var dates=Object.keys(data).sort();if(!dates.length)return;");
        html.append("var end,start;if(root.__year==='last'){end=parseDay(dates[dates.length-1]);start=new Date(end.getTime());start.setDate(start.getDate()-364);}");
        html.append("else{start=new Date(+root.__year,0,1);end=new Date(+root.__year,11,31);}");
        html.append("var cursor=align(start,root.__start);var weeks=[];var max=0,sum=0,blank=0,totalDays=0;");
        html.append("while(cursor<=end){var col=[];for(var i=0;i<7;i++){var d=new Date(cursor.getTime());d.setDate(cursor.getDate()+i);var k=dayKey(d);var inRange=d>=start&&d<=end;var n=inRange?(data[k]||0):null;");
        html.append("if(inRange){totalDays++;sum+=n;if(!n)blank++;if(n>max)max=n;}col.push({d:d,n:n,k:k});}weeks.push(col);cursor.setDate(cursor.getDate()+7);}");
        html.append("var stats=root.querySelector('.bv-heat-stats');stats.textContent='';");
        html.append("[['heat.days','总天数',totalDays],['heat.blank','空白天',blank+' ('+(totalDays?Math.round(blank*100/totalDays):0)+'%)'],['heat.max','最大值',max],['heat.avg','工作日平均次数',workAverage(data,dates)]].forEach(function(pair){");
        html.append("var s=document.createElement('span');s.setAttribute('data-i18n',pair[0]);s.setAttribute('data-zh',pair[1]);s.setAttribute('data-value',String(pair[2]));");
        html.append("var label=(window.__bvLang==='en'&&window.__bvEn&&window.__bvEn[pair[0]])||pair[1];s.textContent=label+' '+pair[2];stats.appendChild(s);});");
        html.append("var plot=root.querySelector('.bv-heat-plot');plot.textContent='';plot.className='bv-heat-plot is-'+root.__size;");
        html.append("var months=document.createElement('div');months.className='bv-heat-months';if(!root.__month)months.hidden=true;");
        html.append("var body=document.createElement('div');body.className='bv-heat-body';");
        html.append("var labels=document.createElement('div');labels.className='bv-heat-wdays';if(!root.__week)labels.hidden=true;");
        html.append("['一','二','三','四','五','六','日'].forEach(function(name,i){var t=document.createElement('span');t.textContent=(i%2===1)?name:'';labels.appendChild(t);});");
        html.append("var grid=document.createElement('div');grid.className='bv-heat-grid';");
        html.append("weeks.forEach(function(col){var month='';col.forEach(function(cell){if(cell.n!=null&&cell.d.getDate()===1)month=(cell.d.getMonth()+1)+'月';});");
        html.append("var m=document.createElement('span');m.textContent=month;months.appendChild(m);");
        html.append("var column=document.createElement('div');column.className='bv-heat-col';");
        html.append("col.forEach(function(cell){var b=document.createElement('i');var lv=cell.n==null?0:level(cell.n,max);b.style.background=colors[lv];");
        html.append("if(cell.n!=null)b.setAttribute('data-tip',dayKey(cell.d)+'  '+cell.n+' 次');column.appendChild(b);});grid.appendChild(column);});");
        html.append("body.appendChild(labels);body.appendChild(grid);plot.appendChild(months);plot.appendChild(body);");
        html.append("if(root.__legend){var legend=document.createElement('div');legend.className='bv-heat-legend';legend.innerHTML='<span>少</span>';colors.forEach(function(c){var i=document.createElement('i');i.style.background=c;legend.appendChild(i);});");
        html.append("var more=document.createElement('span');more.textContent='多';legend.appendChild(more);plot.appendChild(legend);}fit(root);}");
        html.append("function boot(){document.querySelectorAll('.bv-heat').forEach(function(root){");
        html.append("root.__data=JSON.parse(root.querySelector('.bv-heat-data').textContent||'{}');");
        html.append("root.__week=true;root.__month=true;root.__legend=true;root.__size='l';root.__year='last';root.__start=1;");
        html.append("var years={};Object.keys(root.__data).forEach(function(d){years[d.slice(0,4)]=1;});");
        html.append("var sel=root.querySelector('.bv-heat-year');");
        html.append("function yearOpt(id,label){var o=document.createElement('option');o.value=id;o.textContent=label;sel.appendChild(o);}");
        html.append("yearOpt('last','最近一年');Object.keys(years).sort().reverse().forEach(function(y){yearOpt(y,y);});");
        html.append("var lastOpt=sel.querySelector('option[value=\"last\"]');if(lastOpt){lastOpt.setAttribute('data-i18n','heat.lastYear');lastOpt.setAttribute('data-zh','最近一年');}");
        html.append("sel.value='last';");
        html.append("root.addEventListener('click',function(e){var t=e.target.closest('button');if(!t||!root.contains(t))return;");
        html.append("if(t.getAttribute('data-size')){root.__size=t.getAttribute('data-size');root.querySelectorAll('[data-size]').forEach(function(b){b.classList.toggle('is-on',b===t);});paint(root);}");
        html.append("});");
        html.append("sel.addEventListener('change',function(){root.__year=sel.value;paint(root);});");
        html.append("paint(root);});");
        html.append("if(window.ResizeObserver){var ro=new ResizeObserver(function(list){list.forEach(function(en){fit(en.target);});});document.querySelectorAll('.bv-heat').forEach(function(el){ro.observe(el);});}");
        html.append("window.addEventListener('resize',function(){document.querySelectorAll('.bv-heat').forEach(fit);});}");
        html.append("if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();}");
        html.append("</script>");
    }

    private static void signScript(StringBuilder html) {
        html.append("<script>");
        html.append("if(!window.__bvSign){window.__bvSign=true;");
        html.append("function applyOn(on){document.documentElement.classList.toggle('bv-sign-off',!on);");
        html.append("document.querySelectorAll('.bv-sign-switch input').forEach(function(el){el.checked=on;});}");
        html.append("function boot(){var on=true;try{on=localStorage.getItem('bv-sign-show')!=='0';}catch(e){}");
        html.append("applyOn(on);");
        html.append("document.querySelectorAll('.bv-sign-switch input').forEach(function(el){");
        html.append("el.addEventListener('change',function(){try{localStorage.setItem('bv-sign-show',el.checked?'1':'0');}catch(e){}applyOn(el.checked);});});}");
        html.append("if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();}");
        html.append("</script>");
    }

    private static void liveScript(StringBuilder html) {
        html.append("<script>");
        html.append("if(!window.__bvLive){window.__bvLive=true;var pace=1,timer=0;");
        html.append("var hist={cpu:[],mem:[],disk:[],down:[],up:[],read:[],write:[]};var coreHist=[];var gpuHist=[];");
        html.append("function push(k,v){if(v==null||isNaN(v))return;hist[k].push(v);if(hist[k].length>60)hist[k].shift();}");
        html.append("function spark(card,key,color,fill,rate){var svg=card.querySelector('.bv-spark');if(!svg)return;");
        html.append("var a=hist[key],n=a.length,d='',w=320,h=150,max=100,grid='';");
        html.append("if(rate){max=1;for(var k=0;k<n;k++)if(a[k]>max)max=a[k];}");
        html.append("for(var gy=0;gy<=4;gy++){var y1=(h/4)*gy;grid+='<line x1=\"0\" y1=\"'+y1+'\" x2=\"'+w+'\" y2=\"'+y1+'\" stroke=\"#e8edf2\"/>';}");
        html.append("for(var i=0;i<n;i++){var x=n<2?0:(i/(n-1))*w;var y=h-((Math.max(0,a[i])/max)*(h-6))-3;");
        html.append("d+=(i?'L':'M')+x.toFixed(1)+' '+y.toFixed(1);}");
        html.append("var area='';if(fill&&n){area='<path d=\"'+d+' L'+(n<2?0:w)+' '+h+' L0 '+h+' Z\" fill=\"'+color+'\" opacity=\"0.16\"/>';}");
        html.append("svg.innerHTML=grid+area+'<path d=\"'+d+'\" fill=\"none\" stroke=\"'+color+'\" stroke-width=\"1.6\"/>';}");
        html.append("function drawCores(values){if(!values||!values.length)return;");
        html.append("if(coreHist.length!==values.length){coreHist=values.map(function(){return [];});}");
        html.append("values.forEach(function(v,i){coreHist[i].push(v);if(coreHist[i].length>60)coreHist[i].shift();});");
        html.append("document.querySelectorAll('.bv-core-grid').forEach(function(grid){");
        html.append("if(grid.childElementCount!==values.length){grid.textContent='';values.forEach(function(){var cell=document.createElement('div');cell.className='bv-core';cell.innerHTML='<b></b><svg viewBox=\"0 0 80 36\" preserveAspectRatio=\"none\"></svg>';grid.appendChild(cell);});}");
        html.append("values.forEach(function(v,i){var cell=grid.children[i];if(!cell)return;cell.querySelector('b').textContent=Math.round(v)+'%';");
        html.append("var a=coreHist[i],n=a.length,d='',w=80,h=36;for(var k=0;k<n;k++){var x=n<2?0:(k/(n-1))*w;var y=h-(Math.max(0,Math.min(100,a[k]))/100)*(h-4)-2;d+=(k?'L':'M')+x.toFixed(1)+' '+y.toFixed(1);}");
        html.append("cell.querySelector('svg').innerHTML='<path d=\"'+d+' L'+(n<2?0:w)+' '+h+' L0 '+h+' Z\" fill=\"#3479B0\" opacity=\"0.2\"/><path d=\"'+d+'\" fill=\"none\" stroke=\"#3479B0\" stroke-width=\"1.2\"/>';});});}");
        html.append("function drawGpus(list){if(!list||!list.length)return;");
        html.append("if(gpuHist.length!==list.length){gpuHist=list.map(function(){return [];});}");
        html.append("list.forEach(function(g,i){var v=g.util==null?0:g.util;gpuHist[i].push(v);if(gpuHist[i].length>60)gpuHist[i].shift();});");
        html.append("document.querySelectorAll('.bv-gpu-slot').forEach(function(slot){");
        html.append("var key=list.map(function(g){return g.name;}).join('|');");
        html.append("if(slot.getAttribute('data-key')!==key){slot.textContent='';slot.setAttribute('data-key',key);");
        html.append("list.forEach(function(g){var art=document.createElement('article');art.className='bv-tile';");
        html.append("art.innerHTML='<h3></h3><svg class=\"bv-spark\" viewBox=\"0 0 320 150\" preserveAspectRatio=\"none\"></svg><div class=\"bv-series\"><i style=\"background:#50A040\"></i><span class=\"bv-live-sub\"></span><b class=\"bv-live-value\">--</b></div>';");
        html.append("art.querySelector('h3').textContent=g.name||'GPU';slot.appendChild(art);});}");
        html.append("list.forEach(function(g,i){var art=slot.children[i];if(!art)return;var sub=art.querySelector('.bv-live-sub');");
        html.append("sub.textContent=g.memoryText||'占用';art.querySelector('.bv-live-value').textContent=(g.util==null?'--':Math.round(g.util)+'%');");
        html.append("var svg=art.querySelector('svg');var a=gpuHist[i],n=a.length,d='',w=320,h=150,max=100;");
        html.append("for(var k=0;k<n;k++)if(a[k]>max)max=a[k];");
        html.append("var grid='';for(var gy=0;gy<=4;gy++){var y1=(h/4)*gy;grid+='<line x1=\"0\" y1=\"'+y1+'\" x2=\"'+w+'\" y2=\"'+y1+'\" stroke=\"#e8edf2\"/>';}");
        html.append("for(var k=0;k<n;k++){var x=n<2?0:(k/(n-1))*w;var y=h-(Math.max(0,a[k])/max)*(h-6)-3;d+=(k?'L':'M')+x.toFixed(1)+' '+y.toFixed(1);}");
        html.append("svg.innerHTML=grid+'<path d=\"'+d+' L'+(n<2?0:w)+' '+h+' L0 '+h+' Z\" fill=\"#50A040\" opacity=\"0.16\"/><path d=\"'+d+'\" fill=\"none\" stroke=\"#50A040\" stroke-width=\"1.6\"/>';});});}");
        html.append("function cpuMeta(j){var parts=[];if(j.cpuName)parts.push(j.cpuName);if(j.cpuCores)parts.push(j.cpuCores+' 核');if(j.cpuLogical)parts.push(j.cpuLogical+' 逻辑处理器');if(j.cpuMhz)parts.push((j.cpuMhz/1000).toFixed(2)+' GHz');");
        html.append("if(!parts.length)return;document.querySelectorAll('.bv-cpu-meta').forEach(function(el){el.textContent=parts.join(' · ');});}");
        html.append("function gb(n){if(n==null||n<0)return'';var v=n/1073741824;return (v>=10?v.toFixed(1):v.toFixed(2))+' GB';}");
        html.append("function rate(n){if(n==null||isNaN(n))return '--';var u=['B/s','KB/s','MB/s','GB/s'],v=n,i=0;");
        html.append("while(v>=1024&&i<3){v/=1024;i++;}return (i===0?v.toFixed(0):v>=10?v.toFixed(1):v.toFixed(2))+' '+u[i];}");
        html.append("function setCards(key,value,sub){document.querySelectorAll('[data-live=\"'+key+'\"]').forEach(function(card){");
        html.append("card.querySelector('.bv-live-value').textContent=value;if(sub)card.querySelector('.bv-live-sub').textContent=sub;});}");
        html.append("function disks(list){document.querySelectorAll('[data-live=\"disk\"] .bv-disk-list').forEach(function(box){box.textContent='';");
        html.append("(list||[]).forEach(function(d){var row=document.createElement('div');row.className='bv-disk';");
        html.append("var name=document.createElement('span');name.textContent=d.name||'';");
        html.append("var bar=document.createElement('div');bar.className='bv-disk-bar';var i=document.createElement('i');");
        html.append("i.style.width=Math.max(0,Math.min(100,d.percent||0))+'%';bar.appendChild(i);");
        html.append("var meta=document.createElement('span');meta.textContent=(d.usedText||'')+' / '+(d.totalText||'');");
        html.append("row.appendChild(name);row.appendChild(bar);row.appendChild(meta);box.appendChild(row);});});}");
        html.append("async function load(){var q='?pace='+pace;var urls=['machine'+q,'http://127.0.0.1:8765/machine'+q];");
        html.append("if(location.protocol==='file:')urls=['http://127.0.0.1:8765/machine'];");
        html.append("for(var i=0;i<urls.length;i++){try{var r=await fetch(urls[i],{cache:'no-store'});if(r.ok)return await r.json();}catch(e){}}");
        html.append("throw new Error('off');}");
        html.append("async function loadActivity(){var urls=['activity','http://127.0.0.1:8765/activity'];");
        html.append("if(location.protocol==='file:')urls=['http://127.0.0.1:8765/activity'];");
        html.append("for(var i=0;i<urls.length;i++){try{var r=await fetch(urls[i],{cache:'no-store'});if(r.ok)return await r.json();}catch(e){}}");
        html.append("return null;}");
        html.append("function fillJenkins(box,j){box.textContent='';");
        html.append("var src=document.querySelector('.bv-source:not([hidden])');");
        html.append("var preset=src?src.getAttribute('data-last'):'';");
        html.append("var last=document.createElement('p');");
        html.append("if(preset){last.textContent=preset;}");
        html.append("else if(j.lastRun&&j.lastRun.ago){last.textContent='距上次构建 '+j.lastRun.ago+(j.lastRun.building?' 进行中':'')+'  '+(j.lastRun.job||'')+(j.lastRun.number?' #'+j.lastRun.number:'');}");
        html.append("else{last.textContent='距上次构建 无记录';}");
        html.append("box.appendChild(last);");
        html.append("if(j.uptime){var up=document.createElement('p');up.textContent='本次已运行 '+j.uptime;box.appendChild(up);}");
        html.append("function block(title,rows,empty){var h=document.createElement('h4');h.textContent=title;box.appendChild(h);");
        html.append("if(!rows||!rows.length){var p=document.createElement('p');p.textContent=empty;box.appendChild(p);return;}");
        html.append("rows.forEach(function(text){var p=document.createElement('p');p.textContent=text;box.appendChild(p);});}");
        html.append("block('登录',(j.users||[]).map(function(u){return u.name+(u.since?'  '+u.since:'');}), j.preview?'预览读不到登录会话':'无');");
        html.append("block('正在构建',(j.builds||[]).map(function(b){return b.job+' #'+b.number+'  '+b.user;}),'无');");
        html.append("block('产物请求',(j.downloads||[]).map(function(d){return d.user+'  '+d.path+'  '+d.ago;}), j.preview?'预览读不到产物请求':'无');}");
        html.append("async function tick(){var status=document.querySelectorAll('.bv-live-status');");
        html.append("try{var j=await load();push('cpu',j.cpu);push('mem',j.memoryPercent);push('disk',j.diskActive);");
        html.append("push('down',j.netDown);push('up',j.netUp);push('read',j.diskRead);push('write',j.diskWrite);");
        html.append("setCards('cpu',(j.cpu==null?'--':Math.round(j.cpu)+'%'),'占用');");
        html.append("cpuMeta(j);drawCores(j.cores);");
        html.append("setCards('mem',(j.memoryPercent==null?'--':Math.round(j.memoryPercent)+'%'),(gb(j.memoryUsed)||'--')+' / '+(gb(j.memoryTotal)||'--'));");
        html.append("setCards('disk',(j.diskActive==null?'--':Math.round(j.diskActive)+'%'),'活动时间');");
        html.append("drawGpus(j.gpus);");
        html.append("setCards('down',rate(j.netDown),'每秒');setCards('up',rate(j.netUp),'每秒');");
        html.append("setCards('read',rate(j.diskRead),'每秒');setCards('write',rate(j.diskWrite),'每秒');");
        html.append("document.querySelectorAll('[data-live=\"cpu\"]').forEach(function(c){spark(c,'cpu','#3479B0',true);});");
        html.append("document.querySelectorAll('[data-live=\"mem\"]').forEach(function(c){spark(c,'mem','#3C74A3',true);});");
        html.append("document.querySelectorAll('[data-live=\"disk\"]').forEach(function(c){spark(c,'disk','#F08048',true);});");
        html.append("document.querySelectorAll('[data-live=\"down\"]').forEach(function(c){spark(c,'down','#3479B0',true,true);});");
        html.append("document.querySelectorAll('[data-live=\"up\"]').forEach(function(c){spark(c,'up','#3C74A3',true,true);});");
        html.append("document.querySelectorAll('[data-live=\"read\"]').forEach(function(c){spark(c,'read','#F08048',true,true);});");
        html.append("document.querySelectorAll('[data-live=\"write\"]').forEach(function(c){spark(c,'write','#50A040',true,true);});");
        html.append("disks(j.disks);");
        html.append("var act=await loadActivity();");
        html.append("document.querySelectorAll('.bv-jenkins').forEach(function(box){if(act)fillJenkins(box,act);});");
        html.append("status.forEach(function(el){el.textContent='刷新 '+pace+' 秒';});");
        html.append("}catch(e){status.forEach(function(el){el.textContent='采样未连接';});}}");
        html.append("function arm(){if(timer)clearInterval(timer);timer=setInterval(tick,pace*1000);}");
        html.append("document.addEventListener('click',function(e){var cpu=e.target.closest('[data-cpu]');");
        html.append("if(cpu){var mode=cpu.getAttribute('data-cpu');");
        html.append("document.querySelectorAll('[data-live=\"cpu\"]').forEach(function(card){card.classList.toggle('is-cores',mode==='cores');});");
        html.append("document.querySelectorAll('[data-cpu]').forEach(function(el){el.classList.toggle('is-on',el.getAttribute('data-cpu')===mode);});return;}");
        html.append("var b=e.target.closest('[data-pace]');if(!b)return;");
        html.append("pace=b.getAttribute('data-pace')==='1'?1:5;");
        html.append("document.querySelectorAll('[data-pace]').forEach(function(el){el.classList.toggle('is-on',el.getAttribute('data-pace')===String(pace));});");
        html.append("arm();tick();});");
        html.append("tick();arm();}");
        html.append("if(!window.__bvLogo){window.__bvLogo=true;");
        html.append("var logoDefault='").append(pluginAsset("images/logo.jpg")).append("';");
        html.append("function showLogo(url){document.querySelectorAll('.bv-about-logo img').forEach(function(img){img.src=url||logoDefault;});}");
        html.append("function bootAbout(){try{var saved=localStorage.getItem('bv-about-logo');if(saved)showLogo(saved);}catch(e){}");
        html.append("document.querySelectorAll('.bv-star').forEach(function(bar){");
        html.append("try{if(localStorage.getItem('bv-star-hide')==='1')bar.hidden=true;}catch(e){}");
        html.append("var close=bar.querySelector('.bv-star-close');if(!close)return;");
        html.append("close.addEventListener('click',function(){bar.hidden=true;try{localStorage.setItem('bv-star-hide','1');}catch(err){}});});}");
        html.append("if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',bootAbout);else bootAbout();");
        html.append("document.addEventListener('change',function(e){var input=e.target.closest('.bv-logo-file');if(!input||!input.files||!input.files[0])return;");
        html.append("var file=input.files[0];var note=document.querySelector('.bv-about-note');");
        html.append("if(file.size>2*1024*1024){if(note)note.textContent='图片超过 2 MB，请换一张小一些的。';return;}");
        html.append("var reader=new FileReader();reader.onload=function(){var url=String(reader.result||'');");
        html.append("try{localStorage.setItem('bv-about-logo',url);}catch(err){if(note)note.textContent='这张图太大，浏览器存不下。';return;}");
        html.append("showLogo(url);if(note)note.textContent='已使用本机上传的 Logo。';};reader.readAsDataURL(file);});");
        html.append("document.addEventListener('click',function(e){if(!e.target.closest('.bv-logo-clear'))return;");
        html.append("try{localStorage.removeItem('bv-about-logo');}catch(err){}showLogo('');");
        html.append("var note=document.querySelector('.bv-about-note');if(note)note.textContent='已恢复默认 Logo。';});}");
        html.append("</script>");
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

    private static void langScript(StringBuilder html) {
        html.append("<script>");
        html.append("if(!window.__bvI18n){window.__bvI18n=true;");
        html.append("window.__bvEn={");
        html.append("'nav.brand':'Build Stats','nav.overview':'Overview','nav.host':'This computer','nav.about':'About','nav.pipes':'Pipelines','nav.machine':'Host','nav.time':'Time',");
        html.append("'range.7':'7 days','range.30':'30 days','range.90':'90 days','range.all':'All','sign.toggle':'Status plate',");
        html.append("'heat.s':'S','heat.m':'M','heat.l':'L','heat.year':'Year','heat.lastYear':'Last year','heat.days':'Days','heat.blank':'Empty days','heat.max':'Max','heat.avg':'Weekday average',");
        html.append("'chart.hours':'Machine hours','chart.split':'Success, failure, and abort','chart.count':'Build count','chart.hour':'Time of day','chart.rate':'Success rate','chart.result':'Result','chart.mtime':'Machine time','chart.trigger':'Trigger',");
        html.append("'chart.trend':'Duration','chart.stage':'Stage status','chart.names':'Stage name changes','chart.stageTime':'Average stage time','chart.failStage':'Failed stage',");
        html.append("'kpi.rate':'Success rate','kpi.ok':'Successes','kpi.time':'Duration','kpi.count':'Triggers','kpi.failTime':'Failure and abort time','kpi.fail':'Failures',");
        html.append("'host.cpu':'CPU','host.mem':'Memory','host.disk':'Disk','host.down':'Download','host.up':'Upload','host.read':'Disk read','host.write':'Disk write','host.now':'Jenkins now','host.pace':'Refresh','host.total':'Total','host.cores':'Cores',");
        html.append("'about.lang':'Language','about.author':'Author','about.repo':'GitHub','about.upload':'Upload logo','about.restore':'Restore logo',");
        html.append("'about.star':'If Build Stats helps you, please star it on GitHub.','about.starGo':'Star on GitHub',");
        html.append("'about.note':'The author and repository link are fixed. Language follows Jenkins unless you change it here. A replacement logo stays in this browser only.',");
        html.append("'state.run':'Running','state.ok':'Success','state.fail':'Failure','state.unstable':'Unstable','state.abort':'Aborted','state.none':'Not built'");
        html.append("};");
        html.append("function bvLang(){try{var saved=localStorage.getItem('bv-lang');if(saved==='zh'||saved==='en')return saved;}catch(e){}");
        html.append("var lang=(document.documentElement.lang||'').toLowerCase();return lang.indexOf('en')===0?'en':'zh';}");
        html.append("function applyLang(lang){window.__bvLang=lang;document.querySelectorAll('[data-i18n]').forEach(function(el){");
        html.append("var key=el.getAttribute('data-i18n');var zh=el.getAttribute('data-zh');if(!zh){zh=el.textContent;el.setAttribute('data-zh',zh);}");
        html.append("var text=lang==='en'&&window.__bvEn[key]?window.__bvEn[key]:zh;var extra=el.getAttribute('data-value');");
        html.append("el.textContent=extra!=null&&extra!==''?text+' '+extra:text;});");
        html.append("document.querySelectorAll('.bv-lang').forEach(function(sel){sel.value=lang;});}");
        html.append("window.__bvLang=bvLang();");
        html.append("function bootLang(){applyLang(window.__bvLang);document.querySelectorAll('.bv-lang').forEach(function(sel){");
        html.append("sel.addEventListener('change',function(){try{localStorage.setItem('bv-lang',sel.value);}catch(e){}applyLang(sel.value);});});}");
        html.append("if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',bootLang);else bootLang();}");
        html.append("</script>");
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
