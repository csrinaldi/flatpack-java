/*
 * #%L
 * FlatPack serialization code
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
package com.getperka.flatpack;

import java.lang.reflect.Type;

import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackModule;
import com.google.inject.Guice;
import com.google.inject.ImplementedBy;
import com.google.inject.Injector;
import com.google.inject.Stage;

/**
 * The main entry-point to the FlatPack API. This type exists to provide a central point for
 * configuring instances of {@link Packer} and {@link Unpacker}.
 */
@ImplementedBy(FlatPackImpl.class)
public abstract class FlatPack {
  /**
   * Create a new instance of FlatPack.
   */
  public static synchronized FlatPack create(Configuration configuration) {
    Injector createInjector = Guice.createInjector(Stage.PRODUCTION,
        new FlatPackModule(configuration));
    return createInjector.getInstance(FlatPack.class);
  }

  FlatPack() {}

  /**
   * Returns a configured instance of {@link Packer}.
   */
  public abstract Packer getPacker();

  /**
   * Returns a reference to the typesystem introspection logic.
   */
  public abstract TypeContext getTypeContext();

  /**
   * Returns a configured instance of {@link Unpacker}.
   */
  public abstract Unpacker getUnpacker();

  /**
   * Returns {@code true} if the given type is an entity or may contain a reference to an entity.
   */
  public abstract boolean isRootType(Type clazz);
}
