package com.getperka.flatpack.policy;

/*
 * #%L
 * FlatPack Security Policy
 * %%
 * Copyright (C) 2012 - 2013 Perka Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.policy.domain.ExtendsMerchant;
import com.getperka.flatpack.policy.domain.Merchant;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.visitors.PolicyVisitor;
import com.getperka.flatpack.security.GroupPermissions;
import com.getperka.flatpack.security.SecurityAction;
import com.getperka.flatpack.security.SecurityGroup;

/**
 * Verify that a parsed policy file is correctly transformed into the appropriate in-memory objects.
 */
public class PolicyExtractionTest extends PolicyTestBase {

  @Test
  public void test() {
    doTest(loadTestPolicyContents());
  }

  /**
   * Parse the test file, and shuffle the elements to ensure that there aren't any test dependencies
   * on the particulars of how the contents of the file are ordered.
   */
  @Test
  public void testShuffle() {
    PolicyFile policyFile = loadTestPolicy();

    final Random r = new Random(0);
    for (int i = 0; i < 10; i++) {
      policyFile.accept(new PolicyVisitor() {

        /**
         * Shuffle nested lists.
         */
        @Override
        public void traverse(List<? extends PolicyNode> list) {
          if (list != null) {
            Collections.shuffle(list, r);
          }
          super.traverse(list);
        }

        /**
         * Don't re-order the internal state of an Ident.
         */
        @Override
        public boolean visit(Ident<?> x) {
          return false;
        }
      });

      try {
        doTest(policyFile.toSource());
      } catch (IllegalArgumentException e) {
        System.err.println(i + " Failing source:\n" + policyFile.toSource());
        throw e;
      }
    }
  }

  void checkPermissions(GroupPermissions p, String groupName, String... actionNames) {
    Set<SecurityAction> expected = setForIteration();
    for (String name : actionNames) {
      String[] parts = name.split("\\.");
      assertEquals(2, parts.length);
      expected.add(SecurityAction.of(parts[0], parts[1]));
    }
    for (Map.Entry<SecurityGroup, Set<SecurityAction>> entry : p.getOperations().entrySet()) {
      if (groupName.equals(entry.getKey().getName())) {
        assertEquals(expected, entry.getValue());
        return;
      }
    }
    fail("Did not find SecurityGroup named " + groupName + " in " + p);
  }

  /**
   * Examination of the actual structure of the parsed data. The tests performed here must not
   * assume any relative ordering of rules, since this method will be called with shuffled input.
   */
  void doTest(String contents) {
    doTest(contents, Merchant.class);
    doTest(contents, ExtendsMerchant.class);
  }

  void doTest(String contents, Class<? extends Merchant> clazz) {
    FlatPack fp = flatpack(contents);
    GroupPermissions p = fp.getTypeContext().describe(clazz).getGroupPermissions();
    assertNotNull(p);

    // Check various type-level permissions
    checkMerchantPermissions(p);

    // Check property-level permissions, especially type- and global-overrides
    Property name = getProperty(fp.getTypeContext(), clazz, "name");
    p = name.getGroupPermissions();
    assertNotNull(p);
    // Just replacing a previous declaration
    assertEquals(5, p.getOperations().size());
    checkPermissions(p, "*", "crudOperation.read");

    // Test the "allow only" construct
    Property note = getProperty(fp.getTypeContext(), clazz, "note");
    p = note.getGroupPermissions();
    assertNotNull(p);
    assertEquals(1, p.getOperations().size());
    checkPermissions(p, "internalUser", "*.*");

    // Verify that unreferenced properties inherit the type's allow
    Property other = getProperty(fp.getTypeContext(), clazz, "other");
    p = other.getGroupPermissions();
    assertNotNull(p);
    checkMerchantPermissions(p);
  }

  private void checkMerchantPermissions(GroupPermissions p) {
    assertEquals(p.getOperations().toString(), 5, p.getOperations().size());
    checkPermissions(p, "*");
    checkPermissions(p, "merchantUser", "crudOperation.update");
    checkPermissions(p, "integratorUser", "crudOperation.create", "crudOperation.read",
        "crudOperation.update", "crudOperation.delete");
    checkPermissions(p, "clerk", "crudOperation.read");
    checkPermissions(p, "internalUser", "*.*");
  }

  private Property getProperty(TypeContext ctx, Class<? extends HasUuid> clazz, String propertyName) {
    for (Property p : ctx.describe(clazz).getProperties()) {
      if (p.getName().equals(propertyName)) {
        return p;
      }
    }
    return null;
  }
}
