package com.getperka.flatpack;

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.VisitorContext;

public class PackVisitor {
  public <T> void endVisit(FlatPackEntity<T> x, VisitorContext<FlatPackEntity<T>> ctx) {}

  public void endVisit(Property property, VisitorContext<Property> ctx) {}

  public <T extends HasUuid> void endVisit(T entity, VisitorContext<T> ctx) {}

  public <T> void endVisitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {}

  public <T> boolean visit(FlatPackEntity<T> x, VisitorContext<FlatPackEntity<T>> ctx) {
    return true;
  }

  public boolean visit(Property property, VisitorContext<Property> ctx) {
    return true;
  }

  public <T extends HasUuid> boolean visit(T entity, VisitorContext<T> ctx) {
    return true;
  }

  public <T> boolean visitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
    return true;
  }
}
