package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;

import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.inject.Inject;

import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.TypeContext;

public class IdentResolver extends PolicyLocationVisitor {

  private List<String> errors = listForAny();
  @Inject
  private TypeContext typeContext;

  IdentResolver() {}

  public List<String> getErrors() {
    return errors;
  }

  @Override
  public boolean visit(Ident<?> x) {
    if (x.getReferent() != null) {
      return false;
    }

    String simpleName = x.getReferentType().getSimpleName();
    simpleName = Introspector.decapitalize(simpleName);
    Throwable ex;
    try {
      return (Boolean) getClass().getDeclaredMethod(simpleName, Ident.class).invoke(this, x);
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
    error(ex.getMessage());
    return false;
  }

  void error(String error) {
    errors.add("At " + summarizeLocation() + ": " + error);
  }

  boolean property(Ident<Property> x) {
    TypePolicy typePolicy = currentLocation(TypePolicy.class);
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
}
