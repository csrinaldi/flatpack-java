package com.getperka.flatpack.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can be used in place of an {@link Acl} or {@link Acls} to reuse a {@link AclDef} preset.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface AclRef {
  /**
   * The {@link AclDef#name() name} of an ACL preset defined in the same class or package.
   */
  String[] value();
}
