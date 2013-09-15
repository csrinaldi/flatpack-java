/*
 * #%L
 * FlatPack Client
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
package com.getperka.flatpack.client.dto;

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;

import java.util.List;
import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.Property;

/**
 * A description of an entity type.
 */
public class EntityDescription extends BaseHasUuid {
  private String docString;
  private GroupPermissions groupPermissions;
  private boolean persistent;
  private List<Property> properties;
  private EntityDescription supertype;
  private String typeName;

  public EntityDescription(String typeName, List<Property> properties) {
    this.typeName = typeName;
    this.properties = properties;
  }

  EntityDescription() {}

  public String getDocString() {
    return docString;
  }

  public GroupPermissions getGroupPermissions() {
    return groupPermissions;
  }

  public List<Property> getProperties() {
    return properties;
  }

  public EntityDescription getSupertype() {
    return supertype;
  }

  public String getTypeName() {
    return typeName;
  }

  /**
   * Indicates that instance of the the type may be persisted by the server. This hint can be used
   * to reduce payload sizes by transmitting only mutated properties.
   */
  public boolean isPersistent() {
    return persistent;
  }

  public void setDocString(String docString) {
    this.docString = docString;
  }

  public void setGroupPermissions(GroupPermissions groupPermissions) {
    this.groupPermissions = groupPermissions;
  }

  public void setPersistent(boolean persistent) {
    this.persistent = persistent;
  }

  public void setProperties(List<Property> properties) {
    this.properties = properties;
  }

  public void setSupertype(EntityDescription supertype) {
    this.supertype = supertype;
  }

  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  @Override
  protected UUID defaultUuid() {
    if (typeName == null) {
      throw new IllegalStateException();
    }
    return UUID.nameUUIDFromBytes((getClass().getName() + ":" + typeName).getBytes(UTF8));
  }
}