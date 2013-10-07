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

import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.TypeSource;
import com.getperka.flatpack.ext.NoPack;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.HasInjector;
import com.getperka.flatpack.inject.PackScope;
import com.getperka.flatpack.security.CrudOperation;
import com.getperka.flatpack.security.PrincipalMapper;
import com.getperka.flatpack.security.Security;
import com.getperka.flatpack.security.SecurityAction;
import com.getperka.flatpack.security.SecurityGroups;
import com.getperka.flatpack.security.SecurityPolicy;
import com.getperka.flatpack.security.SecurityTarget;

/**
 * Test data-driven access policies.
 */
public class PrincipalSecurityTest extends PolicyTestBase {

  static class MyPrincipal implements Principal {
    private final Person person;

    public MyPrincipal(Person person) {
      this.person = person;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof MyPrincipal)) {
        return false;
      }
      MyPrincipal o = (MyPrincipal) obj;
      return person.equals(o.person);
    }

    @Override
    public String getName() {
      return person.getUuid().toString();
    }

    public Person getPerson() {
      return person;
    }

    @Override
    public int hashCode() {
      return person.hashCode();
    }
  }

  static class MyPrincipalMapper implements PrincipalMapper {
    @Override
    public List<String> getGlobalSecurityGroups(Principal principal) {
      return ((MyPrincipal) principal).getPerson().getGlobalGroups();
    }

    @Override
    public List<Principal> getPrincipals(HasUuid entity) {
      if (entity instanceof Person) {
        return Collections.<Principal> singletonList(new MyPrincipal((Person) entity));
      }
      return null;
    }

    @Override
    public boolean isAccessEnforced(Principal principal, SecurityTarget target) {
      return true;
    }
  }

  static class Person extends BaseHasUuid {
    private Person boss;
    private List<String> globalGroups;
    private String name;
    private List<Person> peers;

    public Person getBoss() {
      return boss;
    }

    @NoPack
    public List<String> getGlobalGroups() {
      return globalGroups;
    }

    public String getName() {
      return name;
    }

    public List<Person> getPeers() {
      return peers;
    }

    public void setBoss(Person boss) {
      this.boss = boss;
    }

    @NoPack
    public void setGlobalGroups(List<String> globalGroups) {
      this.globalGroups = globalGroups;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void setPeers(List<Person> peers) {
      this.peers = peers;
    }
  }

  @Inject
  private SecurityGroups groups;
  @Inject
  private Provider<Security> securities;
  @Inject
  private SecurityPolicy securityPolicy;
  @Inject
  private PackScope packScope;
  private Map<String, Property> personProps = mapForLookup();
  @Inject
  private TypeContext typeContext;

  private static final CrudOperation[] ALL_OPS = {
      CrudOperation.CREATE, CrudOperation.DELETE, CrudOperation.READ, CrudOperation.UPDATE };

  @After
  public void after() {
    packScope.exit();
  }

  @Before
  public void before() {
    String policy = loadTestPolicyContents("PrincipalSecurityTest.policy");
    assertNotNull(policy);
    FlatPack flatpack = FlatPack.create(new Configuration()
        .addTypeSource(new TypeSource() {
          @Override
          public Set<Class<?>> getTypes() {
            return Collections.<Class<?>> singleton(Person.class);
          }
        })
        .withPrincipalMapper(new MyPrincipalMapper())
        .withSecurityPolicy(new StaticPolicy(policy)));
    ((HasInjector) flatpack).getInjector().injectMembers(this);

    for (Property prop : typeContext.describe(Person.class).getProperties()) {
      personProps.put(prop.getName(), prop);
    }

    packScope.enter();
  }

  @Test
  public void testBoss() {
    Person b = new Person();

    Person p = new Person();
    p.setBoss(b);

    // Verify the boss can edit the child object's property
    check(new MyPrincipal(b), p, personProps.get("boss"), true, ALL_OPS);
    // But that it cannot edit its own boss property
    checkMayNot(b, personProps.get("boss"), ALL_OPS);
  }

  @Test
  public void testBossPeers() {
    Person bPeer = new Person();

    Person b = new Person();
    b.setPeers(Collections.singletonList(bPeer));

    Person pPeer = new Person();

    Person p = new Person();
    p.setBoss(b);
    p.setPeers(Collections.singletonList(pPeer));

    // Verify the boss's peer can edit the child object's property
    check(new MyPrincipal(bPeer), p, personProps.get("boss"), true, ALL_OPS);

    // Verify that the person's peers cannot edit the property
    check(new MyPrincipal(pPeer), p, personProps.get("boss"), false, ALL_OPS);
  }

  @Test
  public void testGlobalGroup() {
    Person p = new Person();
    p.setGlobalGroups(Collections.singletonList("global"));

    // Verify that it can edit its own boss property
    checkMay(p, personProps.get("boss"), ALL_OPS);
    checkMayNot(new Person(), personProps.get("boss"), ALL_OPS);
  }

  @Test
  public void testNobody() {
    Person p = new Person();
    check(new MyPrincipal(new Person()), p, null, true, CrudOperation.READ);
    check(new MyPrincipal(new Person()), p, null, false, CrudOperation.UPDATE);
  }

  @Test
  public void testSelf() {
    Person p = new Person();
    checkMay(p, ALL_OPS);
    // Check property with no particular annotation
    checkMay(p, personProps.get("name"), ALL_OPS);
    // Check property inherited from BaseHasUuid
    checkMay(p, personProps.get("uuid"), ALL_OPS);
    // Verify that it can't edit its own boss property
    checkMayNot(p, personProps.get("boss"), ALL_OPS);
  }

  private void check(MyPrincipal principal, Person p, Property property, boolean expect,
      CrudOperation... ops) {
    Security security = securities.get();
    for (CrudOperation op : ops) {
      boolean may;
      if (property == null) {
        may = security.may(principal, SecurityTarget.of(p), SecurityAction.of(op));
      } else {
        may = security.may(principal, SecurityTarget.of(p, property), SecurityAction.of(op));
      }
      assertEquals(p.getUuid() + (expect ? " could not " : " should not ") + op, expect, may);
    }
  }

  private void checkMay(Person p, CrudOperation... ops) {
    check(new MyPrincipal(p), p, null, true, ops);
  }

  private void checkMay(Person p, Property property, CrudOperation... ops) {
    assertNotNull(property);
    check(new MyPrincipal(p), p, property, true, ops);
  }

  private void checkMayNot(Person p, Property property, CrudOperation... ops) {
    assertNotNull(property);
    check(new MyPrincipal(p), p, property, false, ops);
  }
}
