package com.hms.config;

import com.hms.filter.UploadLimits;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Gives ONLY the patient-import upload paths a 50 MiB / 51 MiB multipart ceiling.
 *
 * <p>The servlet container applies multipart limits per mapped servlet, so a route can have a
 * bigger ceiling than the rest of the application only by being served through its own servlet
 * registration. That registration is deliberately a plain {@link HttpServlet}, not a second
 * {@link DispatcherServlet}: Spring Security's path matchers refuse to work when two dispatcher
 * servlets exist. This one owns the multipart configuration Tomcat consults while parsing and
 * then hands the request, as a plain method call (no re-dispatch, so the wrapper — and its
 * limits — stay ours), to the application's one DispatcherServlet. Same controllers, filters,
 * security and advice as every other request.
 *
 * <p>The default servlet, and with it every other multipart endpoint, keeps the application's
 * 5 MiB / 6 MiB ceiling exactly as before, for declared-length and chunked uploads alike.
 */
@Configuration
public class ImportUploadServletConfig {

    public static final String[] IMPORT_SERVLET_PATTERNS = {"/hospital/patients/import/*", "/clinic/patients/import/*"};

    /** Delegates to the main dispatcher; exists so the container has a servlet to hang the multipart config on. */
    public static final class ImportUploadServlet extends HttpServlet {
        private final transient DispatcherServlet dispatcher;

        ImportUploadServlet(DispatcherServlet dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            dispatcher.service(new AsDefaultMapping(request), response);
        }
    }

    /**
     * Presents the request exactly as the default {@code /} servlet mapping would: Spring MVC
     * (6.1+) strips a prefix-mapped servlet's path before matching handler patterns, which would
     * turn {@code /hospital/patients/import/commit} into {@code /commit} and find nothing.
     */
    static final class AsDefaultMapping extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final String fullPath;

        AsDefaultMapping(HttpServletRequest request) {
            super(request);
            String servletPath = request.getServletPath() == null ? "" : request.getServletPath();
            String pathInfo = request.getPathInfo() == null ? "" : request.getPathInfo();
            this.fullPath = servletPath + pathInfo;
        }

        @Override
        public String getServletPath() {
            return fullPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public jakarta.servlet.http.HttpServletMapping getHttpServletMapping() {
            return new jakarta.servlet.http.HttpServletMapping() {
                @Override public String getMatchValue() { return fullPath.startsWith("/") ? fullPath.substring(1) : fullPath; }
                @Override public String getPattern() { return "/"; }
                @Override public String getServletName() { return "dispatcherServlet"; }
                @Override public jakarta.servlet.http.MappingMatch getMappingMatch() { return jakarta.servlet.http.MappingMatch.DEFAULT; }
            };
        }
    }

    @Bean
    public ServletRegistrationBean<ImportUploadServlet> importUploadServlet(
            DispatcherServlet dispatcherServlet,
            org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean mainRegistration) {
        // Boot registers the main dispatcher lazily (load-on-startup -1): it initialises its
        // ServletConfig and handler mappings on its own first request. A request that reaches the
        // import servlet first would delegate into an uninitialised dispatcher, so the main one is
        // made eager. Startup cost only; no behavioural change for any other route.
        mainRegistration.setLoadOnStartup(1);
        ServletRegistrationBean<ImportUploadServlet> registration =
                new ServletRegistrationBean<>(new ImportUploadServlet(dispatcherServlet), IMPORT_SERVLET_PATTERNS);
        registration.setName("importUploadServlet");
        registration.setLoadOnStartup(2);
        registration.setMultipartConfig(new MultipartConfigElement(
                "", UploadLimits.IMPORT_FILE_BYTES, UploadLimits.IMPORT_REQUEST_BYTES, 0));
        return registration;
    }
}
