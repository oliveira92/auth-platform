package com.authplatform.auth.infrastructure.audit;

import com.authplatform.auth.domain.model.AuditEvent;
import com.authplatform.auth.domain.port.out.AuditEventPort;
import com.authplatform.auth.infrastructure.config.AuditProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventHttpAdapter implements AuditEventPort {

    private final AuditProperties properties;

    @Override
    public void publish(AuditEvent event) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            restClient().post()
                .uri("/api/v1/audit/events")
                .body(event)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Audit event publication failed open for {}: {}", event.eventType(), ex.getMessage());
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .build();
    }
}
