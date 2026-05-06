package com.authplatform.auth.domain.port.out;

import com.authplatform.auth.domain.model.AuditEvent;

public interface AuditEventPort {
    void publish(AuditEvent event);
}
