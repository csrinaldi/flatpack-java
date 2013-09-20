package com.getperka.flatpack.policy.visitors;

/*
 * #%L
 * FlatPack Security Policy
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
import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.EntityDescription;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackLogger;
import com.getperka.flatpack.policy.pst.AclRule;
import com.getperka.flatpack.policy.pst.Allow;
import com.getperka.flatpack.policy.pst.Group;
import com.getperka.flatpack.policy.pst.GroupDefinition;
import com.getperka.flatpack.policy.pst.HasName;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PackagePolicy;
import com.getperka.flatpack.policy.pst.PolicyBlock;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.TypePolicy;
import com.getperka.flatpack.policy.pst.Verb;

/**
 * Ensures that all {@link Ident} instances have a valid {@link Ident#getReferent() referent}. This
 * visitor will also remove any inheritance from the nodes that it visits.
 */
public class IdentResolver extends PolicyLocationVisitor {
  private final Deque<NodeScope> currentScope = new ArrayDeque<NodeScope>();
  private Set<String> errors = setForIteration();
  @FlatPackLogger
  @Inject
  private Logger logger;
  private final NodeScope rootScope = new NodeScope();
  @Inject
  private SecurityGroups securityGroups;
  private boolean secondPass;
  @Inject
  private TypeContext typeContext;
  private Set<Ident<?>> unresolved = setForIteration();

  IdentResolver() {}

  @Override
  public void endVisit(PackagePolicy x) {
    currentScope.pop();
  }

  @Override
  public void endVisit(PolicyFile x) {
    currentScope.pop();
  }

  @Override
  public void endVisit(TypePolicy x) {
    currentScope.pop();
  }

  /**
   * Process the PolicyFile.
   */
  public void exec(PolicyFile x) {
    // Loop up to two times to handle forward references
    for (int i = 0; i < 2; i++) {
      unresolved.clear();
      traverse(x);
      if (unresolved.isEmpty() || !errors.isEmpty()) {
        break;
      }
      secondPass = true;
    }
    for (Ident<?> ident : unresolved) {
      errors.add("Unresolved identifier " + ident + " on line " + ident.getLineNumber());
    }
  }

  public Set<String> getErrors() {
    return errors;
  }

  /**
   * Duplicate inherited rules into the {@link Allow}.
   */
  @Override
  public boolean visit(Allow x) {
    Ident<Property> inheritFrom = x.getInheritFrom();
    if (inheritFrom == null) {
      return true;
    }
    Property p = ensureReferent(inheritFrom);
    TypePolicy policy = p == null ? null : findTypePolicy(p);
    if (p == null || policy == null) {
      // Skip for the second pass
      unresolved.add(inheritFrom);
      return false;
    }

    x.setInheritFrom(null);

    /*
     * Make copies of each AclRule to ensure that groups are resolved against the current type's
     * scope.
     */
    List<AclRule> toInherit = listForAny();
    for (Allow inherited : policy.getAllows()) {
      toInherit.addAll(inherited.getAclRules());
    }
    for (ListIterator<AclRule> it = toInherit.listIterator(); it.hasNext();) {
      it.set(new AclRule(it.next()));
    }
    x.getAclRules().addAll(0, toInherit);
    return true;
  }

  /**
   * Duplicate inherited rules into the {@link Group}.
   */
  @Override
  public boolean visit(Group x) {
    Ident<Property> inheritFrom = x.getInheritFrom();
    if (inheritFrom == null) {
      return true;
    }
    Property p = ensureReferent(inheritFrom);
    TypePolicy policy = p == null ? null : findTypePolicy(p);
    if (p == null || policy == null) {
      // Skip for the second pass
      unresolved.add(inheritFrom);
      return false;
    }

    // Prevent loops
    x.setInheritFrom(null);

    /*
     * Set up inherited groups. This map is used to allow simple names to refer to the inherit
     * groups, unless the group name is overridden by a local declaration.
     */
    Map<Ident<SecurityGroup>, GroupDefinition> uniqueNames = mapForIteration();
    for (Group group : policy.getGroups()) {
      // Make a copy of the GroupDefinition that prepends the property being inherited from
      for (GroupDefinition def : group.getDefinitions()) {
        GroupDefinition inherited = new GroupDefinition(def, inheritFrom);
        uniqueNames.put(inherited.getName(), inherited);

        GroupDefinition aliased = new GroupDefinition(def, inheritFrom);
        aliased.setName(aliased.getName().removeLeadingIdent());
        uniqueNames.put(aliased.getName(), aliased);
      }
    }

    // Allow local definitions to override the inherited ones
    for (GroupDefinition def : x.getDefinitions()) {
      uniqueNames.put(def.getName(), def);
    }

    x.getDefinitions().clear();
    x.getDefinitions().addAll(uniqueNames.values());

    return true;
  }

  /**
   * Ensure that the referent is non-null. This method will dispatch to various {@code resolveX}
   * methods.
   */
  @Override
  public boolean visit(Ident<?> x) {
    // Don't re-resolve (possibly complex) identifiers
    if (x.getReferent() != null) {
      return false;
    }

    // Reflective dispatch based on desired referent type
    String simpleName = x.getReferentType().getSimpleName();
    Throwable ex;
    try {
      getClass().getDeclaredMethod("resolve" + simpleName, Ident.class).invoke(this, x);
      return false;
    } catch (IllegalArgumentException e) {
      ex = e;
    } catch (SecurityException e) {
      ex = e;
    } catch (IllegalAccessException e) {
      ex = e;
    } catch (InvocationTargetException e) {
      ex = e.getCause();
    } catch (NoSuchMethodException e) {
      ex = e;
    }
    logger.error("Exception while resolving identifiers", ex);
    error(ex.getMessage());
    return false;
  }

  /**
   * Explicit iteration order to simplify resolver logic.
   */
  @Override
  public boolean visit(PackagePolicy x) {
    currentScope.push(scope().child(x.getName()));
    traverseBlock(x);
    return false;
  }

  /**
   * Explicit iteration order to simplify resolver logic.
   */
  @Override
  public boolean visit(PolicyFile x) {
    currentScope.push(rootScope);
    traverseBlock(x);
    return false;
  }

  /**
   * Explicit control over iteration order to ensure that definitions are visited before references.
   */
  @Override
  public boolean visit(TypePolicy x) {
    currentScope.push(scope().child(x.getName()));
    ensureReferent(x);
    traverse(x.getVerbs());
    traverse(x.getGroups());
    traverse(x.getAllows());
    traverse(x.getPolicies());
    return false;
  }

  /**
   * Automatically populate the current scope with named nodes as they are encountered.
   */
  @Override
  protected void doTraverse(PolicyNode x) {
    if (x instanceof HasName) {
      scope().put((HasName<?>) x);
    }
    super.doTraverse(x);
  }

  /**
   * Map a type name reference onto a real {@link Class} object via the {@link TypeContext}.
   */
  void resolveClass(Ident<Class<? extends HasUuid>> x) {
    EntityDescription desc = typeContext.getEntityDescription(x.getSimpleName());
    if (desc == null) {
      error("Unknown type " + x.getSimpleName());
      return;
    }
    x.setReferent(desc.getEntityType());
  }

  void resolveProperty(Ident<Property> x) {
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    if (typePolicy == null) {
      error("Cannot refer to property outside of a type");
      return;
    }
    Class<? extends HasUuid> clazz = ensureReferent(typePolicy);
    if (clazz == null) {
      // Error already reported
      return;
    }
    for (Property p : typeContext.describe(clazz).getProperties()) {
      if (p.getName().equals(x.getSimpleName())) {
        if (p.getEnclosingType().getTypeName().equals(typePolicy.getName().getSimpleName())) {
          x.setReferent(p);
        } else {
          error("Type " + typePolicy.getName() + " does not define property " + p.getName());
        }

        return;
      }
    }
    error("Could not find property " + x + " in type " + typePolicy.getName());
  }

  /**
   * Convert a simple or compound name into a {@link PropertyPath}, using the currently-enclosing
   * {@link TypePolicy} as the basis for resolving property names.
   */
  void resolvePropertyPath(Ident<PropertyPath> x) {
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    if (typePolicy == null) {
      error("Expecting to be in a type declaration");
      return;
    }
    // Ensure the type name has been resolved
    Class<? extends HasUuid> currentType = ensureReferent(typePolicy);
    if (currentType == null) {
      // Should already be reported
      return;
    }

    // Iterate over the path segments, resolving the property names
    List<String> propertyNames;
    if (x.isCompound()) {
      propertyNames = listForAny();
      for (Ident<?> ident : x.getCompoundName()) {
        propertyNames.add(ident.getSimpleName());
      }
    } else {
      propertyNames = Collections.singletonList(x.getSimpleName());
    }

    PropertyPath toReturn = createPropertyPath(currentType, propertyNames);
    x.setReferent(toReturn);

    // Also update the referents for the compound ids, just in case they're needed later
    if (x.isCompound()) {
      for (int i = 0, j = x.getCompoundName().size(); i < j; i++) {
        x.getCompoundName().get(i).cast(Property.class).setReferent(toReturn.getPath().get(i));
      }
    }
    return;
  }

  void resolveSecurityAction(Ident<SecurityAction> x) {
    // Are we declaring a verb?
    Verb currentVerb = currentLocation(Verb.class);
    if (currentVerb != null) {
      SecurityAction a = new SecurityAction(currentVerb.getName().getSimpleName(),
          x.getSimpleName());
      x.setReferent(a);
      return;
    }

    // Otherwise, it must be a verb reference
    if (x.isCompound()) {
      // *.*, Foo.*, or Foo.bar
      Ident<Verb> verbIdent = x.getCompoundName().get(0).cast(Verb.class);

      if (verbIdent.isWildcard()) {
        // Parser shouldn't allow *.foo, so we can ignore the second part
        x.setReferent(new SecurityAction("*", "*"));
        return;
      }

      Verb verb = scope().get(verbIdent);
      if (verb == null) {
        error("Unknown verb: " + verbIdent.getSimpleName());
        return;
      }
      verbIdent.setReferent(verb);

      Ident<SecurityAction> actionIdent = x.getCompoundName().get(1).cast(SecurityAction.class);
      if (actionIdent.isWildcard()) {
        // Foo.*
        SecurityAction action = new SecurityAction(verbIdent.getSimpleName(), "*");
        actionIdent.setReferent(action);
        x.setReferent(action);
      } else {
        // Find matching verb declaration, e.g. Foo.bar
        for (Ident<SecurityAction> action : verb.getActions()) {
          if (action.equals(x.getCompoundName().get(1))) {
            actionIdent.setReferent(action.getReferent());
            x.setReferent(action.getReferent());
            return;
          }
        }

        error("Unknown verb " + x);
        return;
      }
    } else if (x.isWildcard()) {
      // Interpret "*" as "*.*"
      x.setReferent(new SecurityAction("*", "*"));
    } else {
      // read
      String simpleName = x.getSimpleName();

      Ident<SecurityAction> match = null;
      for (Verb v : scope().get(Verb.class)) {
        for (Ident<SecurityAction> ident : v.getActions()) {
          if (simpleName.equals(ident.getSimpleName())) {
            if (match == null) {
              match = ident;
            } else {
              error("The action name " + simpleName
                + " is ambiguous because it is declared by multiple verbs on lines "
                + match.getLineNumber() + " and " + ident.getLineNumber());
              return;
            }
          }
        }
      }
      if (match == null) {
        error("Unknown action name " + simpleName + ". Is it declared by a verb?");
        return;
      }

      x.setReferent(ensureReferent(match));
    }
  }

  void resolveSecurityGroup(Ident<SecurityGroup> x) {
    if (x.isWildcard()) {
      x.setReferent(securityGroups.getGroupAll());
      return;
    }
    if (x.isReflexive()) {
      x.setReferent(securityGroups.getGroupReflexive());
      return;
    }

    // Determine if a group is being declared
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    GroupDefinition groupDefinition = currentLocation(GroupDefinition.class);
    if (groupDefinition != null) {
      // Ensure that all the PropertyPaths are set up
      List<PropertyPath> paths = ensureReferent(groupDefinition.getPaths());
      Class<? extends HasUuid> type = ensureReferent(typePolicy);
      if (type == null) {
        unresolved.add(typePolicy.getName());
        return;
      }
      SecurityGroup group = securityGroups.getGroup(type,
          groupDefinition.getName().toString(), groupDefinition.toSource(), paths);
      x.setReferent(group);
      return;
    }

    // Find an already-declared group
    GroupDefinition group = scope().get(GroupDefinition.class, x);
    if (group != null) {
      x.setReferent(ensureReferent(group));
      return;
    }

    // Determine if it's a global name
    if (typePolicy == null) {
      x.setReferent(securityGroups.getGroupGlobal(x.getSimpleName()));

      GroupDefinition globalDef = new GroupDefinition();
      globalDef.setLineNumber(x.getLineNumber());
      globalDef.setName(x);
      scope().put(globalDef);
    }
  }

  private PropertyPath createPropertyPath(Class<? extends HasUuid> resolveFrom,
      List<String> propertyNames) {
    List<Property> path = listForAny();
    String lookFor = null;
    it: for (Iterator<String> it = propertyNames.iterator(); it.hasNext();) {
      lookFor = it.next();
      for (Property p : typeContext.describe(resolveFrom).getProperties()) {
        if (lookFor.equals(p.getName())) {
          path.add(p);

          String nextType = nextPropertyPathTypeName(p);
          if (nextType == null) {
            break it;
          }
          resolveFrom = typeContext.getEntityDescription(nextType).getEntityType();
          continue it;
        }
      }
      break it;
    }
    if (path.size() != propertyNames.size()) {
      error("Could not find property named " + lookFor + " in type " + resolveFrom.getName());
      return null;
    }
    return new PropertyPath(path);
  }

  /**
   * Resolves the {@link HasName#getName() name} of the given object.
   */
  private <T> T ensureReferent(HasName<T> named) {
    return ensureReferent(named.getName());
  }

  /**
   * Immediately traverses {@code x} if necessary to resolve its referent. This method does not
   * guarantee that the referent has been resolved.
   * 
   * @return {@link Ident#getReferent()}
   */
  private <T> T ensureReferent(Ident<T> x) {
    if (x.getReferent() != null) {
      return x.getReferent();
    }
    traverse(x);
    if (x.getReferent() == null) {
      unresolved.add(x);
    }
    return x.getReferent();
  }

  private <T> List<T> ensureReferent(List<Ident<T>> x) {
    List<T> toReturn = listForAny();
    for (Ident<T> ident : x) {
      toReturn.add(ensureReferent(ident));
    }
    return toReturn;
  }

  private void error(String error) {
    errors.add("At " + summarizeLocation() + ": " + error);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private TypePolicy findTypePolicy(Property p) {
    String typeName = nextPropertyPathTypeName(p);
    Class ref = Class.class;
    TypePolicy toReturn = scope().get(TypePolicy.class, ref, typeName);

    /*
     * Synthesize an empty TypePolicy on the second pass to stub out any types that are implicitly
     * referenced through property chains but that do not have their own type policy.
     */
    if (toReturn == null && secondPass) {
      Ident ident = new Ident(Class.class, typeName);
      toReturn = new TypePolicy();
      toReturn.setName(ident);
      ensureReferent(ident);
      scope().put(toReturn);
    }
    return toReturn;
  }

  /**
   * Given a Property, return the name of the entity type that {@link PropertyPath#evaluate} look at
   * during its traversal.
   */
  private String nextPropertyPathTypeName(Property p) {
    String fpTypeName;
    Type fpType = p.getType();
    Type listElement = fpType.getListElement();
    Type mapValue = fpType.getMapValue();
    if (fpType.getName() != null) {
      fpTypeName = fpType.getName();
    } else if (listElement != null && listElement.getName() != null) {
      fpTypeName = listElement.getName();
    } else if (mapValue != null && mapValue.getName() != null) {
      fpTypeName = mapValue.getName();
    } else {
      fpTypeName = null;
    }
    return fpTypeName;
  }

  private NodeScope scope() {
    return currentScope.peek();
  }

  private void traverseBlock(PolicyBlock x) {
    traverse(x.getVerbs());
    traverse(x.getAllows());
    traverse(x.getPackagePolicies());
    traverse(x.getTypePolicies());
  }
}
