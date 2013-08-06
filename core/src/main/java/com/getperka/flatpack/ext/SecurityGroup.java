package com.getperka.flatpack.ext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.security.PermitAll;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.security.AclGroup;

/**
 * A definition for an ACL security group.
 * 
 * @see AclGroup
 */
@PermitAll
public class SecurityGroup extends BaseHasUuid {
  public static final SecurityGroup ALL = new SecurityGroup("*", "All principals");

  public static final SecurityGroup EMPTY = new SecurityGroup("", "No principals");

  private String description;
  private List<PropertyPath> paths = Collections.emptyList();
  private String name;

  public SecurityGroup(String name, String description) {
    this.description = description;
    this.name = name;
  }

  SecurityGroup() {}

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

  void setDescription(String description) {
    this.description = description;
  }

  void setName(String name) {
    this.name = name;
  }

  void setPaths(List<PropertyPath> paths) {
    this.paths = Collections.unmodifiableList(new ArrayList<PropertyPath>(paths));
  }
}
