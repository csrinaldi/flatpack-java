package com.getperka.flatpack.policy;

import java.util.List;

public class Verb extends PolicyNode implements HasName<Verb> {
  private List<VerbAction> actions = list();
  private Ident<Verb> name;

  @Override
  public void accept(PolicyVisitor v) {
    if (v.visit(this)) {
      v.traverse(actions);
      v.traverse(name);
    }
    v.endVisit(this);
  }

  public List<VerbAction> getActions() {
    return actions;
  }

  @Override
  public Ident<Verb> getName() {
    return name;
  }

  public void setActions(List<VerbAction> actions) {
    this.actions = actions;
  }

  @Override
  public void setName(Ident<Verb> name) {
    this.name = name;
  }

}