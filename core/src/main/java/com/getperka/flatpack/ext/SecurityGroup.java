package com.getperka.flatpack.ext;

import java.util.Collections;
import java.util.List;

import com.getperka.flatpack.security.AclGroup;

/**
 * A definition for an ACL security group.
 * 
 * @see AclGroup
 */
public class SecurityGroup {

  public static final String ALL = "*";
  public static final String EMPTY = "";

  private static final SecurityGroup ALL_GROUP =
      new SecurityGroup(ALL, "All principals", Collections.<PropertyPath> emptyList());
  private static final SecurityGroup EMPTY_GROUP =
      new SecurityGroup(EMPTY, "No principals", Collections.<PropertyPath> emptyList());

  public static SecurityGroup all() {
    return ALL_GROUP;
  }

  public static SecurityGroup create(String name, String description, List<PropertyPath> paths) {
    if (ALL.equals(name)) {
      return ALL_GROUP;
    }

    if (EMPTY.equals(name)) {
      return EMPTY_GROUP;
    }

    return new SecurityGroup(name, description, paths);
  }

  public static SecurityGroup empty() {
    return EMPTY_GROUP;
  }

  private String description;
  private List<PropertyPath> paths = Collections.emptyList();
  private String name;

  private SecurityGroup(String name, String description, List<PropertyPath> paths) {
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

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return name + " " + paths;
  }
}
