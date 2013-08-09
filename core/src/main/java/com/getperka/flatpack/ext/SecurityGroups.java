package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.getperka.flatpack.security.Acl;
import com.getperka.flatpack.security.AclGroup;
import com.getperka.flatpack.security.AclGroups;
import com.getperka.flatpack.security.Acls;
import com.getperka.flatpack.security.CrudOperation;

/**
 * A memoizing factory for SecurityGroup instances.
 */
@Singleton
public class SecurityGroups {
  private final ConcurrentMap<String, SecurityGroup> allGroups =
      new ConcurrentHashMap<String, SecurityGroup>();
  private final ConcurrentMap<Class<?>, DeclaredSecurityGroups> entityGroups =
      new ConcurrentHashMap<Class<?>, DeclaredSecurityGroups>();

  @Inject
  Provider<DeclaredSecurityGroups> entityGroupsProvider;
  @Inject
  TypeContext typeContext;

  /**
   * Requires injection.
   */
  SecurityGroups() {}

  public SecurityGroup getGlobalGroup(String name) {
    return getGroup(getClass(), name, "Global group " + name,
        Collections.<PropertyPath> emptyList());
  }

  /**
   * Create (or find) a group declared by a particular type.
   */
  public SecurityGroup getGroup(Class<?> owner, String name, String description,
      List<PropertyPath> paths) {

    List<String> parsed = parseName(name);
    if (parsed.size() != 1) {
      throw new IllegalArgumentException("Declared group names must be simple");
    }
    name = parsed.get(0);

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
   * Extract the {@link Acl} and/or {@link Acls} declared by a particular class or method. This
   * method is also aware of {@link PermitAll} and {@link DenyAll} annotations.
   * 
   * @param allGroups the basis for resolving security group names in the acl declarations
   * @param elt the element that declares the permissions
   * @return a summary of the permissions declared by {@code elt} or {@code null} if none have been
   *         declared
   */
  public GroupPermissions getPermissions(DeclaredSecurityGroups allGroups, AnnotatedElement elt) {
    GroupPermissions p = null;

    if (elt.isAnnotationPresent(PermitAll.class)) {
      p = GroupPermissions.permitAll();
    }

    List<Acl> toConvert = listForAny();
    Acls acls = elt.getAnnotation(Acls.class);
    if (acls != null) {
      toConvert.addAll(Arrays.asList(acls.value()));
    }
    Acl annotation = elt.getAnnotation(Acl.class);
    if (annotation != null) {
      toConvert.add(annotation);
    }
    if (!toConvert.isEmpty()) {
      Map<SecurityGroup, Set<CrudOperation>> map = mapForLookup();
      for (Acl acl : toConvert) {
        for (String groupName : acl.groups()) {
          SecurityGroup group = allGroups.resolve(groupName);
          if (group == null) {
            // TODO: Emit warning about unresolved group name
            continue;
          }
          Set<CrudOperation> ops = EnumSet.noneOf(CrudOperation.class);
          ops.addAll(Arrays.asList(acl.ops()));
          map.put(group, ops);
        }
      }
      p = new GroupPermissions();
      p.setOperations(Collections.unmodifiableMap(map));
    }

    if (elt.isAnnotationPresent(DenyAll.class)) {
      p = GroupPermissions.denyAll();
    }

    return p;
  }

  /**
   * Examines a class and extracts all {@link AclGroups} information about the type.
   */
  public synchronized DeclaredSecurityGroups getSecurityGroups(Class<?> entityType) {
    DeclaredSecurityGroups toReturn = entityGroups.get(entityType);
    if (toReturn != null) {
      return toReturn;
    }

    if (entityType == null || Object.class.equals(entityType)) {
      return DeclaredSecurityGroups.empty();
    }

    // Look up the groups inherited from the parent
    DeclaredSecurityGroups parent = getSecurityGroups(entityType.getSuperclass());

    AclGroups groups = entityType.getAnnotation(AclGroups.class);

    // Return the parent's data if there are no groups that have been defined
    if (groups == null) {
      entityGroups.put(entityType, parent);
      return parent;
    }

    // Make a copy of the parent's data and add it to the lookup cache to prevent loops
    toReturn = entityGroupsProvider.get();
    toReturn.copyFrom(parent);
    entityGroups.put(entityType, toReturn);

    // Create information for the directly-declared properties
    for (AclGroup group : groups.value()) {

      List<PropertyPath> paths = new ArrayList<PropertyPath>();
      for (String path : group.path()) {
        paths.add(constructPath(path, entityType));
      }
      // Sort by shortest first
      Collections.sort(paths, new Comparator<PropertyPath>() {
        @Override
        public int compare(PropertyPath o1, PropertyPath o2) {
          return o1.getPath().size() - o2.getPath().size();
        }
      });

      SecurityGroup toAdd = getGroup(entityType, group.name(), group.description(), paths);
      toReturn.getDeclared().put(group.name(), toAdd);
    }

    // Extract groups inherited from referenced entities
    for (Property prop : typeContext.extractProperties(entityType)) {
      if (prop.isInheritGroups()) {
        DeclaredSecurityGroups inherited = getSecurityGroups(prop.getGetter().getReturnType());
        toReturn.getInherited().put(prop.getName(), inherited);
      }
    }

    // Make immutable
    toReturn.setDeclared(Collections.unmodifiableMap(toReturn.getDeclared()));
    toReturn.setInherited(Collections.unmodifiableMap(toReturn.getInherited()));
    return toReturn;
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
    if (SecurityGroup.ALL.equals(parsed.get(0))) {
      switch (parsed.size()) {
        case 1:
          return SecurityGroup.all();
        case 2:
          return getGlobalGroup(parsed.get(1));
        default:
          // Should be prevented by name parsing code
          throw new UnsupportedOperationException();
      }
    }

    for (int i = 0, j = parsed.size(); i < j; i++) {
      String part = parsed.get(i);

      // If we're in the middle of a chain, a.b.c, then just look for inherited groups
      if (i < j - 1) {
        groups = groups.getInherited().get(part);
        if (groups == null) {
          return null;
        }
        continue;
      }

      // Find the terminal group
      toReturn = groups.getDeclared().get(part);
      if (toReturn != null) {
        return toReturn;
      }

      // Look any inherited group with the same simple name
      for (DeclaredSecurityGroups g : groups.getInherited().values()) {
        // Calling g.resolve() uses the group's resolution cache
        toReturn = g.resolve(part);
        if (toReturn != null) {
          return toReturn;
        }
      }

      // Otherwise, treat the terminal symbol as a reference to a global group
      return getGlobalGroup(part);
    }

    // Should never get here
    throw new UnsupportedOperationException(name);
  }

  private PropertyPath constructPath(String path, Class<?> startFrom) {
    // Treat the empty path as a reference to "this"
    if (path.isEmpty()) {
      return new PropertyPath(Collections.<Property> emptyList());
    }

    String[] parts = path.split(Pattern.quote("."));
    List<Property> toReturn = new ArrayList<Property>(parts.length);

    part: for (String part : parts) {
      for (Property p : typeContext.extractProperties(startFrom)) {
        if (p.getName().equals(part)) {
          toReturn.add(p);
          startFrom = typeContext.getClass(p.getEnclosingTypeName());
          continue part;
        }
      }
      throw new IllegalArgumentException("Could not find a property named \"" + part + "\" in "
        + startFrom.getName() + " while constructing path " + path);
    }

    return new PropertyPath(toReturn);
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
