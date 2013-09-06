package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.parboiled.common.FileUtils;
import org.parboiled.parserunners.BasicParseRunner;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.TypeSource;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityAction;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.policy.domain.Clerk;
import com.getperka.flatpack.policy.domain.IntegratorUser;
import com.getperka.flatpack.policy.domain.IsPrincipalMapper;
import com.getperka.flatpack.policy.domain.Merchant;
import com.getperka.flatpack.policy.domain.MerchantLocation;
import com.getperka.flatpack.policy.domain.MerchantUser;
import com.getperka.flatpack.policy.pst.Ident;
import com.getperka.flatpack.policy.pst.PolicyFile;
import com.getperka.flatpack.policy.pst.PolicyNode;
import com.getperka.flatpack.policy.pst.PolicyVisitor;

public class PolicyTest {

  @Test
  public void test() {
    String contents = FileUtils.readAllText(getClass().getResourceAsStream("test.policy"));
    doTest(contents);
  }

  /**
   * Parse the test file, and shuffle the elements to ensure that there aren't any test dependencies
   * on the particulars of how the contents of the file are ordered.
   */
  @Test
  public void testShuffle() {
    String contents = FileUtils.readAllText(getClass().getResourceAsStream("test.policy"));
    BasicParseRunner<Object> runner = new BasicParseRunner<Object>(PolicyParser.get().PolicyFile());
    PolicyFile policyFile = (PolicyFile) runner.run(contents).resultValue;

    final Random r = new Random(0);
    for (int i = 0; i < 10; i++) {
      policyFile.accept(new PolicyVisitor() {

        /**
         * Don't re-order the internal state of an Ident.
         */
        @Override
        public boolean visit(Ident<?> x) {
          return false;
        }

        @Override
        protected void traverse(List<? extends PolicyNode> list) {
          if (list != null) {
            Collections.shuffle(list, r);
          }
          super.traverse(list);
        }
      });

      String source = policyFile.toSource();
      System.out.println("\nRun " + i + "\n" + source);
      doTest(source);
    }
  }

  void checkPermissions(GroupPermissions p, String groupName, String... actionNames) {
    Set<SecurityAction> expected = setForIteration();
    for (String name : actionNames) {
      String[] parts = name.split("\\.");
      assertEquals(2, parts.length);
      expected.add(new SecurityAction(parts[0], parts[1]));
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
    FlatPack fp = flatpack(contents);
    GroupPermissions p = fp.getTypeContext().getGroupPermissions(Merchant.class);
    assertNotNull(p);

    // Check various type-level permissions
    checkMerchantPermissions(p);

    // Check property-level permissions, especially type- and global-overrides
    Property name = getProperty(fp.getTypeContext(), Merchant.class, "name");
    p = name.getGroupPermissions();
    assertNotNull(p);
    // Just replacing a previous declaration
    assertEquals(5, p.getOperations().size());
    checkPermissions(p, "*", "crudOperation.read");

    // Test the "allow only" construct
    Property note = getProperty(fp.getTypeContext(), Merchant.class, "note");
    p = note.getGroupPermissions();
    assertNotNull(p);
    assertEquals(1, p.getOperations().size());
    checkPermissions(p, "internalUser", "*.*");

    // Verify that unreferenced properties inherit the type's allow
    Property other = getProperty(fp.getTypeContext(), Merchant.class, "other");
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

  private FlatPack flatpack(String contents) {
    StaticPolicy securityPolicy = new StaticPolicy(contents);
    FlatPack flatpack = FlatPack.create(
        new Configuration()
            .addTypeSource(new TypeSource() {
              @Override
              public Set<Class<?>> getTypes() {
                Set<Class<?>> toReturn = setForIteration();
                toReturn.addAll(Arrays.<Class<?>> asList(
                    Clerk.class, IntegratorUser.class, Merchant.class,
                    MerchantLocation.class, MerchantUser.class));
                return toReturn;
              }
            })
            .withPrincipalMapper(new IsPrincipalMapper())
            .withSecurityPolicy(securityPolicy));
    return flatpack;
  }

  /**
   * Extract a SecurityGroup with the specified name from the GroupPermissions object.
   */
  private SecurityGroup getGroup(GroupPermissions permissions, String name) {
    for (SecurityGroup g : permissions.getOperations().keySet()) {
      if (name.equals(g.getName())) {
        return g;
      }
    }
    return null;
  }

  private Property getProperty(TypeContext ctx, Class<?> clazz, String propertyName) {
    for (Property p : ctx.extractProperties(clazz)) {
      if (p.getName().equals(propertyName)) {
        return p;
      }
    }
    return null;
  }
}
