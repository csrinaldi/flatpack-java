package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

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

  private boolean didWork;
  private List<String> errors = listForAny();
  @FlatPackLogger
  @Inject
  private Logger logger;
  @Inject
  private SecurityGroups securityGroups;
  @Inject
  private TypeContext typeContext;

  private final Map<String, HasName<?>> namedThings = sortedMapForIteration();

  IdentResolver() {}

  public void exec(PolicyNode x) {
    do {
      didWork = false;
      traverse(x);
    } while (didWork && errors.isEmpty());
  }

  public List<String> getErrors() {
    return errors;
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
   * Explicit control over iteration order to ensure that group definitions are visited first.
   */
  @Override
  public boolean visit(TypePolicy x) {
    traverse(x.getGroups());
    traverse(x.getAllows());
    traverse(x.getPolicies());
    return false;
  }

  @Override
  protected void doTraverse(PolicyNode x) {
    if (x instanceof HasName) {
      putNamedThing((HasName<?>) x);
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
    return false;
  }

  boolean securityAction(Ident<SecurityAction> x) {
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

    // Determine if it's a global name
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
    if (typePolicy == null) {
      x.setReferent(securityGroups.getGroupGlobal(x.getSimpleName()));
      return false;
    }

    // Determine if a group is being declared
    GroupDefinition groupDefinition = currentLocation(GroupDefinition.class);
    if (groupDefinition != null) {
      // Ensure that all the PropertyPaths are set up
      List<PropertyPath> paths = ensureReferent(groupDefinition.getPaths());
      SecurityGroup group = securityGroups.getGroup(ensureReferent(typePolicy.getName()),
          groupDefinition.getName().getSimpleName(), "<DESCRIPTION>", paths);
      x.setReferent(group);
      return false;
    }

    // Find the already-declared group
    GroupDefinition group = getNamedThingInScope(GroupDefinition.class, x);
    if (group != null) {
      x.setReferent(ensureReferent(group.getName()));
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

  boolean verbAction(Ident<VerbAction> x) {
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

  private <T extends HasName<?>> T getNamedThingGlobal(Class<T> clazz, List<String> parts) {
    StringBuilder sb = new StringBuilder(clazz.getName());
    for (String part : parts) {
      sb.append("::").append(part);
    }
    return clazz.cast(namedThings.get(sb.toString()));
  }

  /**
   * Attempts to resolve a named object in the current lexical scope.
   */
  private <T extends HasName<?>> T getNamedThingInScope(Class<T> clazz, Ident<?> simpleName) {
    Deque<String> parts = new LinkedBlockingDeque<String>();
    for (PolicyNode node : currentLocation()) {
      if (node instanceof HasName<?>) {
        parts.addLast(((HasName<?>) node).getName().getSimpleName());
      }
    }

    while (true) {
      StringBuilder sb = new StringBuilder(clazz.getName());
      for (String part : parts) {
        sb.append("::").append(part);
      }
      sb.append("::").append(simpleName);
      T toReturn = clazz.cast(namedThings.get(sb.toString()));
      if (toReturn != null) {
        return toReturn;
      }
      if (parts.isEmpty()) {
        return null;
      }
      parts.removeFirst();
    }
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

  private void putNamedThing(HasName<?> named) {
    List<PolicyNode> scope = new ArrayList<PolicyNode>(currentLocation());
    Collections.reverse(scope);
    StringBuilder sb = new StringBuilder(named.getClass().getName());
    for (PolicyNode n : scope) {
      if (n instanceof HasName) {
        sb.append("::").append(((HasName<?>) n).getName().getSimpleName());
      }
    }
    namedThings.put(sb.toString(), named);
  }
}
