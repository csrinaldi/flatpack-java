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

/**
 * Maps an ACL group name to some number of operations that members of the group may perform.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Acl {
  /**
   * Group names, either defined by an {@link AclGroup}, a global group name, or one of several
   * predefined groups.
   * 
   * @see AclGroup#ALL
   * @see AclGroup#THIS
   */
  String[] groups();

  /**
   * The operations that the group may perform. The default value is all {@link CrudOperation}
   * values.
   */
  CrudOperation[] ops() default { CREATE, DELETE, READ, UPDATE };
}
