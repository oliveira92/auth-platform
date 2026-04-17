package com.authplatform.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.ldap")
public class LdapProperties {
    private String url;
    private String baseDn;
    private String userDn;
    private String password;
    private String userSearchBase = "ou=Users";
    private String userSearchFilter = "(sAMAccountName={0})";
    private String groupSearchBase = "ou=Groups";
    private String groupSearchFilter = "(member={0})";
    private String groupRoleAttribute = "cn";
    private boolean referral = false;
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
}
