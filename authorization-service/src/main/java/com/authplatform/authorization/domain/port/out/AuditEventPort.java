package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.AuditEvent;

public interface AuditEventPort {
    void publish(AuditEvent event);
}
