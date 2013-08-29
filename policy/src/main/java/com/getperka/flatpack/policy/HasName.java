package com.getperka.flatpack.policy;

public interface HasName<P extends PolicyNode & HasName<P>> {
  Ident<P> getName();

  void setName(Ident<P> name);
}