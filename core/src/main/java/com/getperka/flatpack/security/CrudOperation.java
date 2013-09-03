package com.getperka.flatpack.security;

import com.getperka.flatpack.ext.SecurityAction;

public enum CrudOperation {
  CREATE,
  DELETE,
  READ,
  UPDATE;

  public static final SecurityAction CREATE_ACTION = new SecurityAction(CrudOperation.CREATE);
  public static final SecurityAction DELETE_ACTION = new SecurityAction(CrudOperation.DELETE);
  public static final SecurityAction READ_ACTION = new SecurityAction(CrudOperation.READ);
  public static final SecurityAction UPDATE_ACTION = new SecurityAction(CrudOperation.UPDATE);

}
