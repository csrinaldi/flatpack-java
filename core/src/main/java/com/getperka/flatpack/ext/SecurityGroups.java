package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.getperka.flatpack.inject.FlatPackLogger;

/**
 * A memoizing factory for SecurityGroup instances.
 */
@Singleton
public class SecurityGroups {

  @FlatPackLogger
  @Inject
  Logger logger;
  @Inject
  DeclaredSecurityGroups emptyGroups;
  @Inject
  Provider<DeclaredSecurityGroups> entityGroupsProvider;
  @Inject
  SecurityPolicy securityPolicy;
  @Inject
  TypeContext typeContext;

  private final ConcurrentMap<String, SecurityGroup> allGroups =
      new ConcurrentHashMap<String, SecurityGroup>();
  private final SecurityGroup groupAll =
      new SecurityGroup("*", "All principals", Collections.<PropertyPath> emptyList());
  private final SecurityGroup groupEmpty =
      new SecurityGroup("", "No principals", Collections.<PropertyPath> emptyList());
  private final SecurityGroup groupReflexive =
      new SecurityGroup("this", "The principal that represents the entity",
          Collections.singletonList(new PropertyPath(Collections.<Property> emptyList())));

  private GroupPermissions permissionsDenyAll = new GroupPermissions() {
    @Override
    public Map<SecurityGroup, Set<SecurityAction>> getOperations() {
      return Collections.emptyMap();
    }

    @Override
    public String toString() {
      return "Deny all";
    }
  };

  private final GroupPermissions permissionsPermitAll = new GroupPermissions() {
    {
      setOperations(Collections.singletonMap(groupAll,
          Collections.singleton(new SecurityAction("*", "*"))));
    }

    @Override
    public String toString() {
      return "Permit all";
    }
  };

  /**
   * Requires injection.
   */
  SecurityGroups() {}

  /**
   * Create (or find) a group declared by a particular type.
   */
  public SecurityGroup getGroup(Class<?> owner, String name, String description,
      List<PropertyPath> paths) {
    String key = owner.getName() + ":" + name;
    SecurityGroup toReturn = allGroups.get(key);
    if (toReturn != null) {
      return toReturn;
    }

    toReturn = new SecurityGroup(name, description, paths);
    SecurityGroup existing = allGroups.putIfAbsent(key, toReturn);
    return existing == null ? toReturn : existing;
  }

  /**
   * Returns a singleton group representing all principals.
   */
  public SecurityGroup getGroupAll() {
    return groupAll;
  }

  /**
   * Returns a singleton group representing no principals.
   */
  public SecurityGroup getGroupEmpty() {
    return groupEmpty;
  }

  public SecurityGroup getGroupGlobal(String name) {
    SecurityGroup toReturn = getGroup(getClass(), name, "Global group " + name,
        Collections.<PropertyPath> emptyList());
    toReturn.setImplicitSecurityGroup(true);
    return toReturn;
  }

  /**
   * Returns a singleton group for the reflexive group (i.e. the principal that represents the
   * entity).
   */
  public SecurityGroup getGroupReflexive() {
    return groupReflexive;
  }

  /**
   * Allows all requests.
   * 
   * @see PermitAll
   */
  public GroupPermissions getPermissionsAll() {
    return permissionsPermitAll;
  }

  /**
   * Denies all requests.
   * 
   * @see DenyAll
   */
  public GroupPermissions getPermissionsNone() {
    return permissionsDenyAll;
  }

  /**
   * Returns the {@link SecurityGroup} with the given relative name, or
   * {@code SecurityGroup#empty()} if one is not defined.
   * 
   * @param name the name of the security group, which may be a chained reference (e.g.
   *          {@code manager.director}).
   */
  public SecurityGroup resolve(DeclaredSecurityGroups groups, String name) {
    SecurityGroup toReturn;

    List<String> parsed = parseName(name);

    // Handle special case of * and *.foo
    if ("*".equals(parsed.get(0))) {
      switch (parsed.size()) {
        case 1:
          return groupAll;
        case 2:
          return getGroupGlobal(parsed.get(1));
        default:
          // Should be prevented by name parsing code
          throw new UnsupportedOperationException();
      }
    }

    if ("this".equals(parsed.get(0))) {
      switch (parsed.size()) {
        case 1:
          return groupReflexive;
        default:
          throw new IllegalArgumentException("The reflexive group \"this\" cannot be dereferenced");
      }
    }

    List<Property> propertyPrefix = listForAny();
    for (int i = 0, j = parsed.size(); i < j; i++) {
      String part = parsed.get(i);

      // If we're in the middle of a chain, a.b.c, then just look for inherited groups
      if (i < j - 1) {
        for (Entry<Property, DeclaredSecurityGroups> entry : groups.getInherited().entrySet()) {
          if (entry.getKey().getName().equals(part)) {
            propertyPrefix.add(entry.getKey());
            groups = entry.getValue();
          } else {
            return null;
          }
        }
        continue;
      }

      // Find the terminal group, possibly appending the dotted path
      toReturn = groups.getDeclared().get(part);
      if (toReturn != null) {
        if (!propertyPrefix.isEmpty()) {
          toReturn = new SecurityGroup(toReturn, propertyPrefix);
        }
        return toReturn;
      }

      // Look any inherited group with the same simple name
      for (DeclaredSecurityGroups g : groups.getInherited().values()) {
        // Avoid infinite loops for self-referential groups
        if (groups.equals(g)) {
          continue;
        }
        // Calling g.resolve() uses the group's resolution cache
        toReturn = g.resolve(part);
        if (toReturn != null) {
          if (!propertyPrefix.isEmpty()) {
            toReturn = new SecurityGroup(toReturn, propertyPrefix);
          }
          return toReturn;
        }
      }

      // Otherwise, treat the terminal symbol as a reference to a global group
      return getGroupGlobal(part);
    }

    // Should never get here
    throw new UnsupportedOperationException(name);
  }

  private List<String> parseName(String name) {
    name = name.trim();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Names cannot be empty");
    }

    List<String> toReturn = listForAny();
    StringBuilder sb = new StringBuilder();
    boolean wildcard = false;

    for (int i = 0, j = name.length(); i < j; i++) {
      char c = name.charAt(i);
      switch (c) {
      // Handle the wildcard, which must be the first element
        case '*':
          if (i != 0) {
            throw new IllegalArgumentException("Wildcard must be first character in name");
          }
          wildcard = true;
          sb.append(c);
          break;
        // Chained groups are separated by dots
        case '.':
          if (sb.length() == 0) {
            throw new IllegalArgumentException("Unexpected .. seen");
          }
          toReturn.add(sb.toString());
          sb.setLength(0);
          break;
        default:
          // Otherwise, names must be valid identifiers
          switch (sb.length()) {
            case 0:
              if (!Character.isJavaIdentifierStart(c)) {
                throw new IllegalArgumentException("Unexpected start character " + c);
              }
              break;
            default:
              if (!Character.isJavaIdentifierPart(c)) {
                throw new IllegalArgumentException("Unexpected character " + c);
              }
              break;
          }
          sb.append(c);
          break;
      }
    }

    // Add the remaining characters
    toReturn.add(sb.toString());

    if (wildcard && toReturn.size() > 2) {
      throw new IllegalArgumentException("Global group names must be single level");
    }

    return toReturn;
  }

}
