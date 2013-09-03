package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

/**
 * A summary of all security groups both declared and inherited by an entity type.
 */
public class DeclaredSecurityGroups {
  private Map<String, SecurityGroup> declared = Collections.emptyMap();
  private Map<Property, DeclaredSecurityGroups> inherited = Collections.emptyMap();
  private Map<String, SecurityGroup> resolved = new ConcurrentHashMap<String, SecurityGroup>();
  @Inject
  private SecurityGroups securityGroups;

  /**
   * Requires injection.
   */
  protected DeclaredSecurityGroups() {}

  /**
   * Copy initializer. The collections returned by the newly-created instance are guaranteed to be
   * mutable copies of the original data.
   */
  public void copyFrom(DeclaredSecurityGroups other) {
    declared = sortedMapForIteration();
    declared.putAll(other.declared);
    inherited = mapForIteration();
    inherited.putAll(other.inherited);
  }

  /**
   * The security groups declared by the entity type or inherited from its supertype.
   */
  public Map<String, SecurityGroup> getDeclared() {
    return declared;
  }

  /**
   * The security groups inherited by the entity type via its properties. The map key is the group
   * path prefix.
   */
  public Map<Property, DeclaredSecurityGroups> getInherited() {
    return inherited;
  }

  /**
   * Returns the {@link SecurityGroup} with the given relative name, or
   * {@code SecurityGroup#empty()} if one is not defined.
   * 
   * @param name the name of the security group, which may be a chained reference (e.g.
   *          {@code manager.director}).
   */
  public SecurityGroup resolve(String name) {
    SecurityGroup toReturn = resolved.get(name);
    if (toReturn != null) {
      return toReturn;
    }

    toReturn = securityGroups.resolve(this, name);
    if (toReturn == null) {
      toReturn = securityGroups.getGroupEmpty();
    }
    resolved.put(name, toReturn);
    return toReturn;
  }

  public void setDeclared(Map<String, SecurityGroup> declared) {
    Map<String, SecurityGroup> map = mapForIteration();
    map.putAll(declared);
    this.declared = Collections.unmodifiableMap(map);
  }

  public void setInherited(Map<Property, DeclaredSecurityGroups> inherited) {
    Map<Property, DeclaredSecurityGroups> map = mapForIteration();
    map.putAll(inherited);
    this.inherited = map;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return declared + " " + inherited;
  }
}
