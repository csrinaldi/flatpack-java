package com.getperka.flatpack.security;

import static com.getperka.flatpack.security.CrudOperation.CREATE;
import static com.getperka.flatpack.security.CrudOperation.DELETE;
import static com.getperka.flatpack.security.CrudOperation.READ;
import static com.getperka.flatpack.security.CrudOperation.UPDATE;

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

  CrudOperation[] ops() default { CREATE, DELETE, READ, UPDATE };
}
