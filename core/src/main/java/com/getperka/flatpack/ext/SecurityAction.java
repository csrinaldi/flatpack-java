package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackTypes.UTF8;

import java.beans.Introspector;
import java.util.UUID;

import com.getperka.flatpack.BaseHasUuid;

public class SecurityAction extends BaseHasUuid {
  private String action;
  private String type;

  public SecurityAction(Enum<?> e) {
    this.action = e.name().toLowerCase();
    this.type = Introspector.decapitalize(e.getDeclaringClass().getSimpleName());
  }

  public SecurityAction(String type, String action) {
    this.action = action.toLowerCase();
    this.type = Introspector.decapitalize(type);
  }

  SecurityAction() {}

  public String getAction() {
    return action;
  }

  public String getType() {
    return type;
  }

  public boolean isActionWildcard() {
    return "*".equals(action);
  }

  public boolean isVerbWildcard() {
    return "*".equals(type);
  }

  /**
   * Returns {@code true} if a principal who possesses the current SecurityAction would also be
   * allowed to perform {@code desiredAction}.
   */
  public boolean permit(SecurityAction desiredAction) {
    if (desiredAction == null) {
      return false;
    }
    if (this.equals(desiredAction)) {
      return true;
    }
    // Allow-all action
    if ("*".equals(type)) {
      return true;
    }
    // Allow all actions of a specific type
    if (type.equals(desiredAction.type) && "*".equals(action)) {
      return true;
    }
    return false;
  }

  public void setAction(String name) {
    this.action = name;
  }

  public void setType(String type) {
    this.type = type;
  }

  /**
   * For debugging use only.
   */
  @Override
  public String toString() {
    return type + "." + action;
  }

  @Override
  protected UUID defaultUuid() {
    if (getType() == null || getAction() == null) {
      throw new IllegalStateException();
    }
    return UUID.nameUUIDFromBytes((getType() + "::" + getAction()).getBytes(UTF8));
  }
}
