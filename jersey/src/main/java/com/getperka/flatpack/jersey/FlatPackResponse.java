/*
 * #%L
 * FlatPack Jersey integration
 * %%
 * Copyright (C) 2012 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.getperka.flatpack.jersey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.TraversalMode;

/**
 * Used by the API documentation generator to specify the format of the returned data. In addition
 * to providing a description of the
 * <p>
 * If the return type is parameterized (e.g. {@link java.util.List}), the value of the annotation
 * should be a flattened representation of the generic type. For example
 * {@code Map<String, Merchant>} would be
 * 
 * <pre>
 * {@literal @}FlatPackEntity({Map.class, String.class, Merchant.class})
 * </pre>
 * <p>
 * The data from this annotation may be used by client code generators to provide type hints. It has
 * no effect on the server code, however.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlatPackResponse {
  /**
   * Provides information about supplementary entities that can be expected in a payload.
   */
  @Documented
  @Target({})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ExtraEntity {
    /**
     * Additional descriptive text about the extra entities that will be found in the response.
     */
    String description();

    /**
     * A description of the type of the extra entity.
     */
    Class<? extends HasUuid> type();
  }

  /**
   * Additional descriptive text about the payload's value. This is analogous to a JavaDoc
   * {@literal @return}.
   */
  String description() default "";

  /**
   * Descriptions of extra entities not related to the payload's {@code value} that may also be in
   * the payload.
   */
  ExtraEntity[] extra() default {};

  /**
   * The default traversal mode for the payload.
   */
  TraversalMode traversalMode() default TraversalMode.SIMPLE;

  /**
   * A description of the type that can be expected in the payload's {@code value} section. A
   * payload with no specific return value (e.g. a "bag") may use the default value of {@link Void}
   * and instead provide information via {@link #extra()}.
   */
  Class<?>[] value() default Void.class;
}
