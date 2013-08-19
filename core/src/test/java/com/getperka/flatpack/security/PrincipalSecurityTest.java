package com.getperka.flatpack.security;

import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.DenyAll;
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
import com.getperka.flatpack.ext.DeclaredSecurityGroups;
import com.getperka.flatpack.ext.PrincipalMapper;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityGroup;
import com.getperka.flatpack.ext.SecurityGroups;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.inject.HasInjector;
import com.getperka.flatpack.inject.PackScope;

public class PrincipalSecurityTest {

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
    public boolean isAccessEnforced(Principal principal, HasUuid entity) {
      return true;
    }
  }

  @AclGroups({
      @AclGroup(name = "boss", path = "boss"),
      @AclGroup(name = "self", path = ""),
      @AclGroup(name = "peer", path = "peers")
  })
  @Acls({
      @Acl(groups = SecurityGroup.ALL, ops = CrudOperation.READ),
      @Acl(groups = "self")
  })
  static class Person extends BaseHasUuid {
    private Person boss;
    private List<String> globalGroups;
    private String name;
    private List<Person> peers;

    @Acls({
        @Acl(groups = { "boss", "boss.peer", "global" }),
    })
    @InheritGroups
    public Person getBoss() {
      return boss;
    }

    @DenyAll
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
  SecurityGroups groups;
  @Inject
  Provider<Security> securities;
  @Inject
  PackScope packScope;
  @Inject
  TypeContext typeContext;
  private Map<String, Property> personProps = mapForLookup();

  private static final CrudOperation[] ALL_OPS = {
      CrudOperation.CREATE, CrudOperation.DELETE, CrudOperation.READ, CrudOperation.UPDATE };

  @After
  public void after() {
    packScope.exit();
  }

  @Before
  public void before() {
    FlatPack flatpack = FlatPack.create(new Configuration()
        .withPrincipalMapper(new MyPrincipalMapper())
        .addTypeSource(new TypeSource() {
          @Override
          public Set<Class<?>> getTypes() {
            return Collections.<Class<?>> singleton(Person.class);
          }
        }));
    ((HasInjector) flatpack).getInjector().injectMembers(this);

    for (Property prop : typeContext.extractProperties(Person.class)) {
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

    Person p = new Person();
    p.setBoss(b);

    // Verify the boss's peer can edit the child object's property
    check(new MyPrincipal(bPeer), p, personProps.get("boss"), true, ALL_OPS);
  }

  @Test
  public void testGlobalGroup() {
    Person p = new Person();
    p.setGlobalGroups(Collections.singletonList("global"));

    // Verify that it can edit its own boss property
    checkMay(p, personProps.get("boss"), ALL_OPS);
  }

  @Test
  public void testGroups() {
    DeclaredSecurityGroups declared = groups.getSecurityGroups(Person.class);
    declared.getDeclared();
    assertEquals(declared.getDeclared().keySet().toString(), 3, declared.getDeclared().size());
    assertNotNull(declared.getDeclared().get("boss"));
    assertNotNull(declared.getDeclared().get("peer"));
    assertNotNull(declared.getDeclared().get("self"));

    assertTrue(personProps.get("boss").isInheritGroups());
    assertEquals(declared.getInherited().keySet().toString(), 1, declared.getInherited().size());
    assertNotNull(declared.getInherited().get("boss"));
    assertSame(declared, declared.getInherited().get("boss"));
  }

  @Test
  public void testSelf() {
    Person p = new Person();
    checkMay(p, ALL_OPS);
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
        may = security.may(principal, p, op);
      } else {
        may = security.may(principal, p, property, op);
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

  private void checkMayNot(Person p, CrudOperation... ops) {
    check(new MyPrincipal(p), p, null, false, ops);
  }

  private void checkMayNot(Person p, Property property, CrudOperation... ops) {
    assertNotNull(property);
    check(new MyPrincipal(p), p, property, false, ops);
  }
}
