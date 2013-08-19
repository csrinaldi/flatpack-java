package com.getperka.flatpack.ext;

import static com.getperka.flatpack.security.AclGroup.ALL;
import static com.getperka.flatpack.security.AclGroup.EMPTY;
import static com.getperka.flatpack.security.AclGroup.THIS;
import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;

import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.security.Acl;
import com.getperka.flatpack.security.AclDef;
import com.getperka.flatpack.security.AclDefs;
import com.getperka.flatpack.security.AclGroup;
import com.getperka.flatpack.security.AclGroups;
import com.getperka.flatpack.security.AclRef;
import com.getperka.flatpack.security.Acls;
import com.getperka.flatpack.security.CrudOperation;

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
  TypeContext typeContext;

  private final ConcurrentMap<String, SecurityGroup> allGroups =
      new ConcurrentHashMap<String, SecurityGroup>();
  private final ConcurrentMap<Class<?>, DeclaredSecurityGroups> entityGroups =
      new ConcurrentHashMap<Class<?>, DeclaredSecurityGroups>();
  private final SecurityGroup groupAll =
      new SecurityGroup(ALL, "All principals", Collections.<PropertyPath> emptyList());
  private final SecurityGroup groupEmpty =
      new SecurityGroup(EMPTY, "No principals", Collections.<PropertyPath> emptyList());
  private final SecurityGroup groupReflexive =
      new SecurityGroup(THIS, "The principal that represents the entity",
          Collections.singletonList(new PropertyPath(Collections.<Property> emptyList())));

  private GroupPermissions permissionsDenyAll = new GroupPermissions() {
    @Override
    public Map<SecurityGroup, Set<CrudOperation>> getOperations() {
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
          Collections.unmodifiableSet(EnumSet.allOf(CrudOperation.class))));
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
      p = permissionsPermitAll;
    }

    List<Acl> toConvert = listForAny();

    // Look up and convert any ACL preset references
    AclRef refs = elt.getAnnotation(AclRef.class);
    if (refs != null) {
      Map<String, List<Acl>> defs = extractAclDefs(elt);
      for (String ref : refs.value()) {
        List<Acl> list = defs.get(ref);
        if (list == null) {
          logger.warn("Unresolved AclRef name {} found in {}", ref, elt);
        } else {
          toConvert.addAll(list);
        }
      }
    }

    // Look for any Acls / Acl annotations on the target
    Acls acls = elt.getAnnotation(Acls.class);
    if (acls != null) {
      toConvert.addAll(Arrays.asList(acls.value()));
    }
    Acl annotation = elt.getAnnotation(Acl.class);
    if (annotation != null) {
      toConvert.add(annotation);
    }

    // Convert any acls that were found
    if (!toConvert.isEmpty()) {
      Map<SecurityGroup, Set<CrudOperation>> map = mapForIteration();
      for (Acl acl : toConvert) {
        for (String groupName : acl.groups()) {
          SecurityGroup group = allGroups.resolve(groupName);
          if (group == null) {
            logger.warn("Unresolved AclGroup name {} found in {}", groupName, elt);
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

    // A DenyAll has the highest precedence
    if (elt.isAnnotationPresent(DenyAll.class)) {
      p = permissionsDenyAll;
    }

    return p;
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
   * Examines a class and extracts all {@link AclGroups} information about the type.
   */
  public synchronized DeclaredSecurityGroups getSecurityGroups(Class<?> entityType) {
    DeclaredSecurityGroups toReturn = entityGroups.get(entityType);
    if (toReturn != null) {
      return toReturn;
    }

    if (entityType == null || Object.class.equals(entityType)) {
      return emptyGroups;
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
      if (group.path().length == 0) {
        throw new IllegalArgumentException(entityType.getName() + " defines an AclGroup named "
          + group.name() + " which does not specify any paths");
      }

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
        toReturn.getInherited().put(prop, inherited);
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
    if (AclGroup.ALL.equals(parsed.get(0))) {
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

    if (AclGroup.THIS.equals(parsed.get(0))) {
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

  private PropertyPath constructPath(String path, Class<?> startFrom) {
    // Treat the empty path as a reference to "this"
    if (path.isEmpty()) {
      throw new IllegalArgumentException(startFrom.getName()
        + " declares an AclGroup with an empty path");
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

  /**
   * Extracts any {@link AclDef} or {@link AclDefs} annotations on a method, class or package.
   */
  private Map<String, List<Acl>> extractAclDefs(AnnotatedElement elt) {

    if (elt instanceof Method) {
      return extractAclDefs(((Method) elt).getDeclaringClass());
    }

    Map<String, List<Acl>> toReturn = sortedMapForIteration();

    if (elt instanceof Class) {
      // Extract all defs from enclosing packages
      String[] parts = ((Class<?>) elt).getPackage().getName().split(Pattern.quote("."));
      StringBuilder sb = new StringBuilder();
      for (String part : parts) {
        if (sb.length() > 0) {
          sb.append(".");
        }
        sb.append(part);
        Package pkg = Package.getPackage(sb.toString());
        if (pkg != null) {
          toReturn.putAll(extractAclDefs(pkg));
        }
      }
    }

    List<AclDef> toMap = listForAny();
    AclDefs defs = elt.getAnnotation(AclDefs.class);
    if (defs != null) {
      toMap.addAll(Arrays.asList(defs.value()));
    }
    AclDef def = elt.getAnnotation(AclDef.class);
    if (def != null) {
      toMap.add(def);
    }

    for (AclDef d : toMap) {
      toReturn.put(d.name(), Arrays.asList(d.acl()));
    }

    return toReturn;
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
