package com.getperka.flatpack.policy;

public interface HasInheritFrom<R> {
  Ident<R> getInheritFrom();

  void setInheritFrom(Ident<R> inheritFrom);
}