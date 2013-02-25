package com.getperka.flatpack;

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

import java.lang.reflect.Type;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.getperka.flatpack.codexes.ValueCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.HasInjector;
import com.google.inject.Injector;

/**
 * Implements HasInjector without exposing it on the public FlatPack API.
 */
@Singleton
class FlatPackImpl extends FlatPack implements HasInjector {
  @Inject
  private Injector injector;
  @Inject
  private Packer packer;
  @Inject
  private TypeContext types;
  @Inject
  private Unpacker unpacker;
  @Inject
  private Visitors visitors;

  FlatPackImpl() {}

  @Override
  public Injector getInjector() {
    return injector;
  }

  @Override
  public Packer getPacker() {
    return packer;
  }

  @Override
  public TypeContext getTypeContext() {
    return types;
  }

  @Override
  public Unpacker getUnpacker() {
    return unpacker;
  }

  public Visitors getVisitors() {
    return visitors;
  }

  @Override
  public boolean isRootType(Type clazz) {
    Codex<?> codex = types.getCodex(clazz);
    return codex != null && !(codex instanceof ValueCodex);
  }
}
