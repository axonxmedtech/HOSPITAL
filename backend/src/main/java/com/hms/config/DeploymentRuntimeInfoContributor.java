package com.hms.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adds {@code "runtime": {"pid": <n>}} to {@code /actuator/info}, and nothing else.
 *
 * <p>Deployment verification (scripts/deploy/await-release.sh) requires this PID to equal the
 * MainPID systemd reports for the service it just restarted. That binds the HTTP answer to the
 * restarted process: a healthy response from another service, another instance, the old JVM or
 * a wrong port carries a different PID and is refused.
 *
 * <p>Deliberately not Spring Boot's process contributor, which also publishes the OS account
 * name, the parent PID and the CPU count. This endpoint is public; a PID grants nothing and
 * changes on every restart, so it is the only process detail exposed.
 */
@Component
public class DeploymentRuntimeInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("runtime", Map.of("pid", ProcessHandle.current().pid()));
    }
}
