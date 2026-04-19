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
    private String userSearchBase = "ou=users";
    private String userSearchFilter = "(uid={0})";
    private String groupSearchBase = "ou=groups";
    private String groupSearchFilter = "(memberUid={0})";
    private String groupRoleAttribute = "cn";
    // Attribute that uniquely identifies the user (uid for OpenLDAP, sAMAccountName for AD)
    private String usernameAttribute = "uid";
    // LDAP objectClass used for groups (posixGroup for OpenLDAP, group for AD)
    private String groupObjectClass = "posixGroup";
    // Attribute that links a user to a group (memberUid for OpenLDAP, member for AD)
    private String groupMemberAttribute = "memberUid";
    // When true, group member value is the username; when false, it is the user's full DN (AD)
    private boolean groupMemberIsUsername = true;
    private boolean referral = false;
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
}
