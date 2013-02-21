package com.getperka.flatpack;

import javax.inject.Inject;

import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext.IterableContext;
import com.getperka.flatpack.ext.VisitorContext.SingletonContext;

public class VisitorSupport {

  @Inject
  TypeContext typeContext;

  VisitorSupport() {}

  public <T> FlatPackEntity<T> visit(PackVisitor visitor, FlatPackEntity<T> entity)
      throws Exception {
    SingletonContext<FlatPackEntity<T>> ctx = new SingletonContext<FlatPackEntity<T>>();
    if (visitor.visit(entity, ctx)) {
      if (entity.getValue() != null) {
        SingletonContext<T> valueContext = new SingletonContext<T>();
        @SuppressWarnings("unchecked")
        Codex<T> codex = (Codex<T>) typeContext.getCodex(entity.getType());

        valueContext.acceptSingleton(visitor, entity.getValue(), codex);
        if (valueContext.didReplace()) {
          entity.withValue(valueContext.getValue());
        }
      }
      Codex<HasUuid> extraCodex = typeContext.getCodex(HasUuid.class);
      new IterableContext<HasUuid>().acceptIterable(visitor, entity.getExtraEntities(),
          extraCodex);
    }
    visitor.endVisit(entity, ctx);
    if (ctx.didReplace()) {
      entity = ctx.getValue();
    }
    return entity;
  }
}
