package com.getperka.flatpack.policy;

import java.util.List;

public class Verb extends PolicyNode implements HasName<Verb> {
  private Ident<Verb> name;
  private List<Ident<Object>> verbIdents = list();

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      // No sub-nodes
    }
    v.endVisit(this);
  }

  @Override
  public Ident<Verb> getName() {
    return name;
  }

  public List<Ident<Object>> getVerbIdents() {
    return verbIdents;
  }

  @Override
  public void setName(Ident<Verb> name) {
    this.name = name;
  }

  public void setVerbIdents(List<Ident<Object>> verbs) {
    this.verbIdents = verbs;
  }
}