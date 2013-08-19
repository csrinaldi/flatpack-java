/**
 * Demonstrates acl presets defined at the package level.
 */
@AclDef(name = "readOnly", acl = @Acl(groups = AclGroup.ALL, ops = CrudOperation.READ))
package com.getperka.flatpack.demo.server;

import com.getperka.flatpack.security.Acl;
import com.getperka.flatpack.security.AclDef;
import com.getperka.flatpack.security.AclGroup;
import com.getperka.flatpack.security.CrudOperation;

