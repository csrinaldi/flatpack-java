package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;

import com.getperka.flatpack.security.Acl;
import com.getperka.flatpack.security.Acls;
import com.getperka.flatpack.security.CrudOperation;

/**
 * Associates some number of {@link SecurityGroup SecurityGroups} with their respective
 * {@link CrudOperation} permissions.
 * 
 * @see Acl
 */
public class GroupPermissions {
  private static final GroupPermissions DENY_ALL = new GroupPermissions() {
    @Override
    public boolean allow(SecurityGroup group, CrudOperation op) {
      return false;
    }

    @Override
    public String toString() {
      return "Deny all";
    }
  };

  private static final GroupPermissions PERMIT_ALL = new GroupPermissions() {
    @Override
    public boolean allow(SecurityGroup group, CrudOperation op) {
      return true;
    }

    @Override
    public String toString() {
      return "Permit all";
    }
  };

  /**
   * Denies all requests.
   * 
   * @see DenyAll
   */
  public static final GroupPermissions denyAll() {
    return DENY_ALL;
  }

  /**
   * Extract the {@link Acl} and/or {@link Acls} declared by a particular class or method. This
   * method is also aware of {@link PermitAll} and {@link DenyAll} annotations.
   * 
   * @param elt the element that declares the permissions
   * @param allGroups the basis for resolving security group names in the acl declarations
   * @return a summary of the permissions declared by {@code elt} or {@code null} if none have been
   *         declared
   */
  public static GroupPermissions extract(AnnotatedElement elt, SecurityGroups allGroups) {
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
   * Allows all requests.
   * 
   * @see PermitAll
   */
  public static final GroupPermissions permitAll() {
    return PERMIT_ALL;
  }

  private Map<SecurityGroup, Set<CrudOperation>> operations = Collections.emptyMap();

  /**
   * Determines if the given group may perform the requested operation. If there is no mapping for
   * the group, the {@link SecurityGroup#all()} will be checked.
   */
  public boolean allow(SecurityGroup group, CrudOperation op) {
    Set<CrudOperation> set = operations.get(group);
    if (set == null) {
      set = operations.get(SecurityGroup.all());
    }
    return set == null ? false : set.contains(op);
  }

  public Map<SecurityGroup, Set<CrudOperation>> getOperations() {
    return operations;
  }

  void setOperations(Map<SecurityGroup, Set<CrudOperation>> operations) {
    this.operations = operations;
  }
}
