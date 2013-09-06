package com.getperka.flatpack.policy.pst;

public interface HasInheritFrom<R> {
  Ident<R> getInheritFrom();

  void setInheritFrom(Ident<R> inheritFrom);
}