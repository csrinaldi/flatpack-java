package com.getperka.flatpack.security;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Allows multiple {@link Acl} annotations to be applied to a type or method.
 */
@Documented
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface Acls {
  Acl[] value();
}
