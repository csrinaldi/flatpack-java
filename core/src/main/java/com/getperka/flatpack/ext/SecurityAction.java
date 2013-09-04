package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;

import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;

public class SecurityAction extends BaseHasUuid {
  private String type;
  private String name;

  public SecurityAction(Enum<?> e) {
    this.name = e.name();
    this.type = e.getDeclaringClass().getSimpleName();
  }

  public SecurityAction(String type, String name) {
    this.name = name;
    this.type = type;
  }

  SecurityAction() {}

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  protected UUID defaultUuid() {
    if (getType() == null || getName() == null) {
      throw new IllegalStateException();
    }
    return UUID.nameUUIDFromBytes((getType() + "::" + getName()).getBytes(UTF8));
  }
}
