package com.getperka.flatpack.policy;

public interface HasInheritFrom<P extends PolicyNode & HasInheritFrom<P>> {
  Ident<P> getInheritFrom();

  void setInheritFrom(Ident<P> inheritFrom);
}