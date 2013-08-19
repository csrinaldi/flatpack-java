package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.Collections;
import java.util.List;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.security.AclGroup;

/**
 * A definition for an ACL security group.
 * 
 * @see AclGroup
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
