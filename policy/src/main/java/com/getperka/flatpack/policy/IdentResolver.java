package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;

import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.PropertyPath;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.FlatPackLogger;

public class IdentResolver extends PolicyLocationVisitor {

  static class Scope {
    private final Map<String, HasName<?>> namedThings = sortedMapForIteration();
    private final Scope parent;

    public Scope() {
      parent = null;
    }

    private Scope(Scope parent) {
      this.parent = parent;
    }

    public <T extends HasName<?>> List<T> get(Class<T> clazz) {
      List<T> toReturn = listForAny();
      for (HasName<?> named : namedThings.values()) {
        if (clazz.isInstance(named)) {
          toReturn.add(clazz.cast(named));
        }
      }
      if (parent != null) {
        toReturn.addAll(parent.get(clazz));
      }
      return toReturn;
    }

    public <T extends HasName<?>> T get(Class<T> clazz, Ident<?> ident) {
      return get(clazz, ident.getSimpleName());
    }

    public <T extends HasName<?>> T get(Class<T> clazz, String simpleName) {
      Object toReturn = namedThings.get(clazz.getName() + "::" + simpleName);
      if (clazz.isInstance(toReturn)) {
        return clazz.cast(toReturn);
      }
      return parent == null ? null : parent.get(clazz, simpleName);
    }

    public Scope newScope() {
      return new Scope(this);
    }

    public void put(HasName<?> named) {
      namedThings.put(named.getClass().getName() + "::" + named.getName().getSimpleName(), named);
    }
  }

  private final Deque<Scope> currentScope = new ArrayDeque<Scope>();
  private boolean didWork;
  private List<String> errors = listForAny();
  @FlatPackLogger
  @Inject
  private Logger logger;
  @Inject
  private SecurityGroups securityGroups;
  @Inject
  private TypeContext typeContext;

  IdentResolver() {}

  @Override
  public void endVisit(PolicyFile x) {
    currentScope.pop();
  }

  @Override
  public void endVisit(TypePolicy x) {
    currentScope.pop();
  }

  public void exec(PolicyNode x) {
    do {
      didWork = false;
      traverse(x);
    } while (didWork && errors.isEmpty());
  }

  public List<String> getErrors() {
    return errors;
  }

  /**
   * Duplicate inherited rules into the Group.
   */
  @Override
  public boolean visit(Group x) {
    if (x.getInheritFrom() != null) {
      Property p = ensureReferent(x.getInheritFrom());
      String typeName = nextPropertyPathTypeName(p);
      TypePolicy policy = scope().get(TypePolicy.class, typeName);
      if (policy == null) {
        // Skip for the second pass
        // XXX tracking for unresolved identifiers
        return false;
      }

      // Prevent loops
      x.setInheritFrom(null);
      didWork = true;

      // Copy the inherited groups, but prepend the via-Property to the GroupDefinitions
      Map<String, GroupDefinition> allDefs = mapForIteration();
      for (Group group : policy.getGroups()) {
        for (GroupDefinition def : group.getDefinitions()) {
          allDefs.put(def.getName().getSimpleName(), new GroupDefinition(def, p));
        }
      }
      for (GroupDefinition def : x.getDefinitions()) {
        allDefs.put(def.getName().getSimpleName(), def);
      }
      x.getDefinitions().clear();
      x.getDefinitions().addAll(allDefs.values());
    }
    return true;
  }

  @Override
  public boolean visit(Ident<?> x) {
    // Don't re-resolve (possibly complex) identifiers
    if (x.getReferent() != null) {
      return false;
    }

    String simpleName = x.getReferentType().getSimpleName();
    simpleName = Introspector.decapitalize(simpleName);
    if ("class".equals(simpleName)) {
      simpleName = "clazz";
    }
    Throwable ex;
    try {
      boolean result = (Boolean) getClass().getDeclaredMethod(simpleName, Ident.class)
          .invoke(this, x);
      didWork |= x.getReferent() != null;
      return result;
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
  public boolean visit(PolicyFile x) {
    currentScope.push(new Scope());
    traverse(x.getVerbs());
    traverse(x.getAllows());
    traverse(x.getTypePolicies());
    return false;
  }

  /**
   * Explicit control over iteration order to ensure that definitions are visited before references.
   */
  @Override
  public boolean visit(TypePolicy x) {
    currentScope.push(currentScope.peek().newScope());
    traverse(x.getVerbs());
    traverse(x.getGroups());
    traverse(x.getAllows());
    traverse(x.getPolicies());
    return false;
  }

  @Override
  protected void doTraverse(PolicyNode x) {
    if (x instanceof HasName) {
      scope().put((HasName<?>) x);
    }
    super.doTraverse(x);
  }

  protected <T> T ensureReferent(Ident<T> x) {
    if (x.getReferent() != null) {
      return x.getReferent();
    }
    traverse(x);
    if (x.getReferent() == null) {
      throw new IllegalStateException("Could not resolve referent for ident " + x);
    }
    return x.getReferent();
  }

  protected <T> List<T> ensureReferent(List<Ident<T>> x) {
    List<T> toReturn = listForAny();
    for (Ident<T> ident : x) {
      toReturn.add(ensureReferent(ident));
    }
    return toReturn;
  }

  boolean clazz(Ident<Class<? extends HasUuid>> x) {
    Class<? extends HasUuid> clazz = typeContext.getClass(x.getSimpleName());
    x.setReferent(clazz);
    return false;
  }

  void error(String error) {
    errors.add("At " + summarizeLocation() + ": " + error);
  }

  boolean object(Ident<Object> x) {
    error("Escaped Ident<Object>. Should not see this.");
    return false;
  }

  boolean property(Ident<Property> x) {
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    if (typePolicy == null) {
      error("Cannot refer to property outside of a type");
      return false;
    }
    String simpleName = typePolicy.getName().getSimpleName();
    Class<?> clazz = typeContext.getClass(simpleName);
    if (clazz == null) {
      error("Unknown type " + simpleName);
      return false;
    }
    for (Property p : typeContext.extractProperties(clazz)) {
      if (p.getName().equals(x.getSimpleName())) {
        x.setReferent(p);
        return false;
      }
    }
    error("Could not find property " + x + " in type " + typePolicy.getName());
    return false;
  }

  boolean propertyPath(Ident<PropertyPath> x) {
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    if (typePolicy == null) {
      error("Expecting to be in a type declaration");
      return false;
    }
    // Ensure the type name has been resolved
    traverse(typePolicy.getName());
    Class<?> currentType = ensureReferent(typePolicy.getName());
    if (currentType == null) {
      // Should already be reported
      return false;
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
    return false;
  }

  boolean securityAction(Ident<SecurityAction> x) {
    // Are we declaring a verb?
    Verb currentVerb = currentLocation(Verb.class);
    if (currentVerb != null) {
      SecurityAction a = new SecurityAction(currentVerb.getName().getSimpleName(),
          x.getSimpleName());
      x.setReferent(a);
      return false;
    }

    // Otherwise, it must be a verb reference
    if (x.isCompound()) {
      // Foo.bar
      Ident<Verb> verbIdent = x.getCompoundName().get(0).cast(Verb.class);
      Verb verb = scope().get(Verb.class, verbIdent);
      if (verb == null) {
        error("Unknown verb: " + verbIdent.getSimpleName());
        return false;
      }
      verbIdent.setReferent(verb);

      Ident<SecurityAction> actionIdent = x.getCompoundName().get(1).cast(SecurityAction.class);
      if (actionIdent.isWildcard()) {
        SecurityAction action = new SecurityAction(verbIdent.getSimpleName(), "*");
        actionIdent.setReferent(action);
        x.setReferent(action);
      } else {
        // Find matching verb declaration
        for (Ident<SecurityAction> action : verb.getActions()) {
          if (action.getSimpleName().equals(x.getSimpleName())) {
            actionIdent.setReferent(action.getReferent());
            x.setReferent(action.getReferent());
            return false;
          }
        }

        error("Unknown verb " + x);
        return false;
      }
    } else if (x.isWildcard()) {
      // XXX Need a WildcardSecurityAction ?
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
              return false;
            }
          }
        }
      }
      if (match == null) {
        error("Unknown action name " + simpleName + ". Is it declared by a verb?");
        return false;
      }

      //
      x.setReferent(ensureReferent(match));
    }

    return false;
  }

  boolean securityGroup(Ident<SecurityGroup> x) {
    if (x.isWildcard()) {
      x.setReferent(securityGroups.getGroupAll());
      return false;
    }
    if (x.isReflexive()) {
      x.setReferent(securityGroups.getGroupReflexive());
      return false;
    }

    // Determine if a group is being declared
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    GroupDefinition groupDefinition = currentLocation(GroupDefinition.class);
    if (groupDefinition != null) {
      // Ensure that all the PropertyPaths are set up
      List<PropertyPath> paths = ensureReferent(groupDefinition.getPaths());
      SecurityGroup group = securityGroups.getGroup(ensureReferent(typePolicy.getName()),
          groupDefinition.getName().getSimpleName(), "<DESCRIPTION>", paths);
      x.setReferent(group);
      return false;
    }

    // Find an already-declared group
    GroupDefinition group = scope().get(GroupDefinition.class, x);
    if (group != null) {
      x.setReferent(ensureReferent(group.getName()));
      return false;
    }

    // Determine if it's a global name
    if (typePolicy == null) {
      x.setReferent(securityGroups.getGroupGlobal(x.getSimpleName()));

      GroupDefinition globalDef = new GroupDefinition();
      globalDef.setLineNumber(x.getLineNumber());
      globalDef.setName(x);
      scope().put(globalDef);
      return false;
    }

    // // Check for inherited groups
    // for (Group g : typePolicy.getGroups()) {
    // if (g.getInheritFrom() != null) {
    // Property from = ensureReferent(g.getInheritFrom());
    // String inheritedTypeName = nextPropertyPathTypeName(from);
    //
    // GroupDefinition inheritedGroup = getNamedThingInScope(GroupDefinition.class,
    // Arrays.asList(inheritedTypeName, x.getSimpleName()));
    // List<PropertyPath> inheritedPaths = ensureReferent(inheritedGroup.getPaths());
    //
    // // Create a new group with the prepended property
    //
    // typePolicy = getNamedThingInScope(TypePolicy.class, new Ident<Object>(Object.class,
    // inheritedTypeName));
    // if (typePolicy == null) {
    // error("Could not find any type declaration for inherited type " + inheritedTypeName
    // + " referenced via property " + from.getName());
    // return false;
    // }
    //
    // }
    // }
    //
    // XXX inherited groups?
    return false;
  }

  boolean verb(Ident<Verb> x) {
    x.setReferent(scope().get(Verb.class, x));
    return false;
  }

  private PropertyPath createPropertyPath(Class<?> resolveFrom, List<String> propertyNames) {
    List<Property> path = listForAny();
    String lookFor = null;
    it: for (Iterator<String> it = propertyNames.iterator(); it.hasNext();) {
      lookFor = it.next();
      for (Property p : typeContext.extractProperties(resolveFrom)) {
        if (lookFor.equals(p.getName())) {
          path.add(p);

          String nextType = nextPropertyPathTypeName(p);
          if (nextType == null) {
            break it;
          }
          resolveFrom = typeContext.getClass(nextType);
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

  private Scope scope() {
    return currentScope.peek();
  }
}
