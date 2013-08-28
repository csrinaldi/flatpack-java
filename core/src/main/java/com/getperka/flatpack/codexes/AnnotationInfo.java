package com.getperka.flatpack.codexes;
/*
 * #%L
 * FlatPack serialization code
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
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

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * This interface is implemented by all annotations produced by {@link AnnotationCodex} to allow
 * map-based access to annotation values.
 */
public interface AnnotationInfo {
  /**
   * Returns the annotation type name specified in the payload. Because annotation types missing
   * from the classpath will be replaced with an {@link UnknownAnnotation}, the
   * {@link Annotation#annotationType()} method may not always return useful information.
   */
  String getAnnotationTypeName();

  /**
   * Returns an immutable view of the annotation's property values.
   */
  Map<String, Object> getAnnotationValues();
}