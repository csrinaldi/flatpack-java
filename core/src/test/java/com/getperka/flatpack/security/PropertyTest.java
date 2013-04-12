/*
 * #%L
 * FlatPack serialization code
 * %%
 * Copyright (C) 2012 Perka Inc.
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
package com.getperka.flatpack.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import org.junit.Before;
import org.junit.Test;

import com.getperka.flatpack.RoleMapper;
import com.getperka.flatpack.ext.PropertySecurity;

public class PropertyTest {

  static class FakeRoleMapper implements RoleMapper {
    @Override
    public Class<?> mapRole(String roleName) {
      return PropertyTest.class;
    }
  }

  @RolesAllowed("foobar")
  static class HasGetterOverride {
    @RolesAllowed("override")
    public String getString() {
      return null;
    }

    public void setString(String value) {}
  }

  @RolesAllowed("foobar")
  static class HasMethod {
    static void noAnnotation() {}
  }

  @RoleDefaults(getters = @RolesAllowed("getters"), setters = @RolesAllowed("setters"))
  static class HasRoleDefaults {
    public String getString() {
      return null;
    }

    public void setString(String string) {}
  }

  @DenyAll
  static void denyAll() {}

  static void noAnnotation() {}

  @PermitAll
  static void permitAll() {}

  @RolesAllowed("foobar")
  static void rolesAllowedOne() {}

  @RolesAllowed({})
  static void rolesAllowedZero() {}

  private RolePropertySecurity security;

  @Before
  public void before() {
    security = new RolePropertySecurity();
    security.inject(null, new FakeRoleMapper());
  }

  @Test
  public void testCheckRoles() {
    Set<Class<?>> empty = Collections.<Class<?>> emptySet();
    Set<Class<?>> full = Collections.<Class<?>> singleton(PropertyTest.class);
    Set<Class<?>> other = Collections.<Class<?>> singleton(UUID.class);
    Set<String> foobarRole = Collections.singleton("foobar");

    // Access allowed with allRoles
    assertTrue(security.checkRoles(security.allRoles, null));
    assertTrue(security.checkRoles(security.allRoles, null));

    // Access denied with empty set
    assertFalse(security.checkRoles(null, null));
    assertFalse(security.checkRoles(empty, null));

    // Access allowed
    assertTrue(security.checkRoles(full, foobarRole));
    assertTrue(security.checkRoles(Collections.<Class<?>> singleton(Object.class), foobarRole));
    assertFalse(security.checkRoles(other, foobarRole));
  }

  @Test
  public void testGetterOverride() throws SecurityException, NoSuchMethodException {
    assertEquals(Collections.singleton("override"),
        security.extractRoleNames(HasGetterOverride.class.getDeclaredMethod("getString"), false,
            true));
    assertEquals(
        PropertySecurity.noRoleNames,
        security.extractRoleNames(
            HasGetterOverride.class.getDeclaredMethod("setString", String.class), true, false));
  }

  @Test
  public void testNoMapper() {
    security.inject(null, null);
    // Access allowed when no RoleMapper is installed
    assertTrue(security.checkRoles(Collections.<Class<?>> emptySet(), null));
  }

  @Test
  public void testRoleDefaults() throws SecurityException, NoSuchMethodException {
    assertEquals(
        Collections.singleton("getters"),
        security.extractRoleNames(HasRoleDefaults.class.getDeclaredMethod("getString"), false,
            true));
    assertEquals(
        Collections.singleton("setters"),
        security.extractRoleNames(
            HasRoleDefaults.class.getDeclaredMethod("setString", String.class), true, false));
  }

  @Test
  public void testRoleNameExtraction() {
    assertSame(Collections.emptySet(), names("denyAll"));
    assertSame(PropertySecurity.noRoleNames, names("noAnnotation"));
    assertSame(PropertySecurity.allRoleNames, names("permitAll"));
    assertEquals(Collections.singleton("foobar"), names("rolesAllowedOne"));
    assertSame(Collections.emptySet(), names("rolesAllowedZero"));
    assertEquals(Collections.singleton("foobar"), names(HasMethod.class, "noAnnotation"));
  }

  private Set<String> names(Class<?> clazz, String methodName) {
    try {
      Method method = clazz.getDeclaredMethod(methodName);
      return security.extractRoleNames(method, true, true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private Set<String> names(String methodName) {
    return names(getClass(), methodName);
  }
}
