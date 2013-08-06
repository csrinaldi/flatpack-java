package com.getperka.flatpack.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Acl {
  String[] groups();

  CrudOperation[] ops() default {
      CrudOperation.CREATE, CrudOperation.DELETE, CrudOperation.READ, CrudOperation.UPDATE };
}
