package com.getperka.flatpack.ext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.getperka.flatpack.BaseHasUuid;

/**
 * A summary of all security groups both declared and inherited by an entity type.
 */
public class SecurityGroups extends BaseHasUuid {
  private static final SecurityGroup NULL = new SecurityGroup();
  private List<SecurityGroup> declared = Collections.emptyList();
  private Map<String, SecurityGroups> inherited = Collections.emptyMap();
  private Map<String, SecurityGroup> resolved = new ConcurrentHashMap<String, SecurityGroup>();

  /**
   * The security groups declared by the entity type.
   */
  public List<SecurityGroup> getDeclared() {
    return declared;
  }

  /**
   * The security groups inherited by the entity type. The map key is the group path prefix.
   */
  public Map<String, SecurityGroups> getInherited() {
    return inherited;
  }

  /**
   * Returns the {@link SecurityGroup} with the given relative name, or {@code null} if one is not
   * defined.
   * 
   * @param name the name of the security group, which may be a chained reference (e.g.
   *          {@code manager.director}).
   */
  public SecurityGroup resolve(String name) {
    SecurityGroup toReturn = resolved.get(name);
    if (toReturn != null) {
      return toReturn;
    }

    // If it's a dotted name,
    int idx = name.indexOf('.');
    if (idx != -1) {
      SecurityGroups next = inherited.get(name.substring(0, idx));
      return next == null ? null : next.resolve(name.substring(idx + 1));
    }

    // Look for groups declared on the entity type
    for (SecurityGroup g : declared) {
      if (name.equals(g.getName())) {
        toReturn = g;
        break;
      }
    }

    // Look for inherited groups, allowing
    if (toReturn == null) {

    }
    return null;
  }

  void setDeclared(List<SecurityGroup> declared) {
    this.declared = Collections.unmodifiableList(new ArrayList<SecurityGroup>(declared));
  }

  void setInherited(Map<String, SecurityGroups> inherited) {
    this.inherited = Collections.unmodifiableMap(new TreeMap<String, SecurityGroups>(inherited));
  }
}
