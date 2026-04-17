package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.Application;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository {
    Application save(Application application);
    Optional<Application> findById(String id);
    Optional<Application> findByClientId(String clientId);
    List<Application> findAll();
    void delete(String id);
}
