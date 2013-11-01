package com.getperka.flatpack.security;

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

/**
 * An enum that defines {@link SecurityAction actions} for manipulating objects in an entity graph.
 * These actions are used by the serialization code to enforce the installed security policy.
 */
public enum CrudOperation {
  CREATE,
  DELETE,
  READ,
  UPDATE;

  public static final SecurityAction CREATE_ACTION = SecurityAction.of(CrudOperation.CREATE);
  public static final SecurityAction DELETE_ACTION = SecurityAction.of(CrudOperation.DELETE);
  public static final SecurityAction READ_ACTION = SecurityAction.of(CrudOperation.READ);
  public static final SecurityAction UPDATE_ACTION = SecurityAction.of(CrudOperation.UPDATE);

}
