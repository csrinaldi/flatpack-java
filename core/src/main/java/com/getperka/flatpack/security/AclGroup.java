package com.getperka.flatpack.security;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.getperka.flatpack.ext.PrincipalMapper;

/**
 * Defines an access control group. The principals that comprise each ACL group are determined by
 * evaluating some number of property paths and passing the retrieved values into the
 * {@link PrincipalMapper}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface AclGroup {
  /**
   * The name of a group representing all principals.
   */
  public static final String ALL = "*";
  /**
   * The name of a group representing no principals.
   */
  public static final String EMPTY = "";
  /**
   * The name of a group representing the principal that the declaring entity is resolved to.
   */
  public static final String THIS = "this";

  /**
   * An additional documentation string.
   */
  String description() default "";

  /**
   * The simple name of the group. The name specified here will be referenced by {@link Acl}
   * annotations.
   */
  String name();

  /**
   * One or more dotted names that represent the property chains that should be evaluated when
   * computing the principals that comprise the group.
   */
  String[] path();
}
