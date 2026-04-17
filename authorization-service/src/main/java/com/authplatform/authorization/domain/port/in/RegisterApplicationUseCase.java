package com.authplatform.authorization.domain.port.in;

import com.authplatform.authorization.domain.model.Application;

public interface RegisterApplicationUseCase {
    Application register(RegisterApplicationCommand command);
    Application update(String applicationId, RegisterApplicationCommand command);
    void deactivate(String applicationId);
}
