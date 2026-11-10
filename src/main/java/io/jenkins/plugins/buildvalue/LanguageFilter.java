package io.jenkins.plugins.buildvalue;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Optional language for this browser.
 *
 * <p>With no cookie, Jenkins' own locale is used, and a language this plugin does not translate
 * falls back to the English bundle. {@code bv-lang=zh} or {@code bv-lang=en} overrides that for
 * the rest of the request, so the About page switch and the pages reached from it agree.
 */
public final class LanguageFilter implements Filter {
    static final String COOKIE = "bv-lang";
    private static final LanguageFilter INSTANCE = new LanguageFilter();

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void install() throws ServletException {
        if (!PluginServletFilter.hasFilter(INSTANCE)) {
            PluginServletFilter.addFilter(INSTANCE);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest http) {
            Locale locale = localeOf(cookie(http));
            if (locale != null) {
                chain.doFilter(new Localized(http, locale), response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** {@code zh} or {@code en} when the cookie is one of those, otherwise no override. */
    static Locale localeOf(String raw) {
        if ("zh".equals(raw)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if ("en".equals(raw)) {
            return Locale.ENGLISH;
        }
        return null;
    }

    private static String cookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static final class Localized extends HttpServletRequestWrapper {
        private final Locale locale;

        Localized(HttpServletRequest request, Locale locale) {
            super(request);
            this.locale = locale;
        }

        @Override
        public Locale getLocale() {
            return locale;
        }

        @Override
        public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(locale));
        }
    }
}
