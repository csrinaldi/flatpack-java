package com.getperka.flatpack.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows {@link Acl} annotations to be reused by name.
 * 
 * @see AclRef
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
public @interface AclDef {
  /**
   * The ACL rules that are being defined.
   */
  Acl[] acl();

  /**
   * The name to be referenced in an {@link AclRef}.
   */
  String name();
}
