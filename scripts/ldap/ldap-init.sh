#!/bin/sh
# Populates OpenLDAP with bootstrap data (OUs, users, groups).
# Runs once after the OpenLDAP container is healthy.
# Uses -c (continue on error) so restarts are idempotent — entries that
# already exist produce error 68 which is safely ignored.
ldapadd -c -x \
  -H ldap://openldap:389 \
  -D "cn=admin,dc=authplatform,dc=com" \
  -w admin \
  -f /ldap/bootstrap.ldif
exit 0
