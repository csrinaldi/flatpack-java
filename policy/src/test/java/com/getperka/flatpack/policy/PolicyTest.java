package com.getperka.flatpack.policy;

import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.parboiled.common.FileUtils;
import org.parboiled.parserunners.BasicParseRunner;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.TypeSource;
import com.getperka.flatpack.ext.GroupPermissions;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.policy.domain.Clerk;
import com.getperka.flatpack.policy.domain.IntegratorUser;
import com.getperka.flatpack.policy.domain.IsPrincipalMapper;
import com.getperka.flatpack.policy.domain.Merchant;
import com.getperka.flatpack.policy.domain.MerchantLocation;
import com.getperka.flatpack.policy.domain.MerchantUser;

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

      doTest(policyFile.toSource());
    }
  }

  /**
   * Examination of the actual structure of the parsed data. The tests performed here must not
   * assume any relative ordering of rules, since this methed will be called with shuffled input.
   */
  void doTest(String contents) {
    FlatPack fp = flatpack(contents);
    GroupPermissions p = fp.getTypeContext().getGroupPermissions(Merchant.class);
    assertNotNull(p);
    assertEquals(p.getOperations().toString(), 3, p.getOperations().size());
    assertNotNull(getGroup(p, "clerk"));
    assertNotNull(getGroup(p, "merchantUser"));
    assertNotNull(getGroup(p, "integratorUser"));

    // flatpack.getTypeContext().extractProperties(Merchant.class);
    // GroupPermissions p = securityPolicy.getPermissions(Merchant.class);
    // System.out.println(p);
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
}
