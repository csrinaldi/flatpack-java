package com.getperka.flatpack.ext;
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

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.Collections;
import java.util.List;

import com.getperka.flatpack.BaseHasUuid;

/**
 * A definition for an ACL security group.
 */
public class SecurityGroup extends BaseHasUuid {
  private String description;
  private boolean implicitSecurityGroup;
  private List<PropertyPath> paths = Collections.emptyList();
  private String name;

  SecurityGroup() {}

  SecurityGroup(SecurityGroup parent, List<Property> pathPrefix) {
    this.description = parent.description;
    this.name = parent.name;
    this.paths = listForAny();
    for (PropertyPath path : parent.paths) {
      List<Property> newProperties = listForAny();
      newProperties.addAll(pathPrefix);
      newProperties.addAll(path.getPath());
      paths.add(new PropertyPath(newProperties));
    }
  }

  SecurityGroup(String name, String description, List<PropertyPath> paths) {
    this.description = description;
    this.name = name;
    this.paths = paths;
  }

  public String getDescription() {
    return description;
  }

  public String getName() {
    return name;
  }

  /**
   * The property paths that define the group.
   */
  public List<PropertyPath> getPaths() {
    return paths;
  }

  public boolean isImplicitSecurityGroup() {
    return implicitSecurityGroup;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return name + " " + paths;
  }

  void setDescription(String description) {
    this.description = description;
  }

  void setImplicitSecurityGroup(boolean implicitSecurityGroup) {
    this.implicitSecurityGroup = implicitSecurityGroup;
  }

  void setName(String name) {
    this.name = name;
  }

  void setPaths(List<PropertyPath> paths) {
    this.paths = paths;
  }
}
