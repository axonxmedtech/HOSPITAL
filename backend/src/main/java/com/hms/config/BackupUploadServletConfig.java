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
 * Gives ONLY the disaster recovery backup upload path a 500 MiB / 505 MiB multipart ceiling.
 *
 * <p>The default servlet, and with it every other multipart endpoint, keeps the application's
 * 5 MiB / 6 MiB ceiling exactly as before, as asserted by UploadBoundaryTest.
 */
@Configuration
public class BackupUploadServletConfig {

    public static final String[] BACKUP_SERVLET_PATTERNS = {"/platform/backups/upload"};

    public static final class BackupUploadServlet extends HttpServlet {
        private final transient DispatcherServlet dispatcher;

        BackupUploadServlet(DispatcherServlet dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            dispatcher.service(new ImportUploadServletConfig.AsDefaultMapping(request), response);
        }
    }

    @Bean
    public ServletRegistrationBean<BackupUploadServlet> backupUploadServlet(
            DispatcherServlet dispatcherServlet,
            org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean mainRegistration) {
        mainRegistration.setLoadOnStartup(1);
        ServletRegistrationBean<BackupUploadServlet> registration =
                new ServletRegistrationBean<>(new BackupUploadServlet(dispatcherServlet), BACKUP_SERVLET_PATTERNS);
        registration.setName("backupUploadServlet");
        registration.setLoadOnStartup(3);
        registration.setMultipartConfig(new MultipartConfigElement(
                "", UploadLimits.BACKUP_FILE_BYTES, UploadLimits.BACKUP_REQUEST_BYTES, 0));
        return registration;
    }
}
