package com.getperka.flatpack.policy.pst;

public interface HasName<R> {
  Ident<R> getName();

  void setName(Ident<R> name);
}