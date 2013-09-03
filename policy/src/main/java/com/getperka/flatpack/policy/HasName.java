package com.getperka.flatpack.policy;

public interface HasName<R> {
  Ident<R> getName();

  void setName(Ident<R> name);
}