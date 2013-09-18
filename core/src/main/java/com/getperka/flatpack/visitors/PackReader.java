package com.getperka.flatpack.visitors;

/*
 * #%L
 * FlatPack serialization code
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
import static com.getperka.flatpack.security.CrudOperation.CREATE_ACTION;
import static com.getperka.flatpack.security.CrudOperation.DELETE_ACTION;
import static com.getperka.flatpack.security.CrudOperation.UPDATE_ACTION;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.inject.Inject;
import javax.inject.Provider;

import com.getperka.flatpack.FlatPackVisitor;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.codexes.EntityCodex;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.SecurityTarget;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.inject.PackScoped;
import com.getperka.flatpack.security.PackSecurity;
import com.google.gson.JsonObject;

/**
 * Populates the properties of individual entities.
 */
@PackScoped
public class PackReader extends FlatPackVisitor {
  static class State {
    HasUuid entity;
  }

  @Inject
  private DeserializationContext context;
  @Inject
  private Provider<ImpliedPropertySetter> impliedPropertySetters;
  private JsonObject payload;
  @Inject
  private PackSecurity security;
  private final Deque<PackReader.State> stack = new ArrayDeque<PackReader.State>();
  @Inject
  private TypeContext typeContext;

  /**
   * Requires injection.
   */
  protected PackReader() {}

  @Override
  public void endVisit(Property prop, VisitorContext<Property> ctx) {
    context.pushPath("." + prop.getName());
    try {
      // Ignore properties that cannot be set
      if (prop.getSetter() == null) {
        return;
      }
      String simplePropertyName = prop.getName();

      /*
       * The UUID property is either set by EntityCodex.allocate() for newly-created objects or
       * would have already been set for persistent entities. In the case of persistent entities,
       * the EntityResolver may have chosen to return an entity with a UUID other than the one
       * requested.
       */
      if (simplePropertyName.equals("uuid")) {
        return;
      }

      context.pushPath("." + simplePropertyName);
      try {
        Object value;
        if (prop.isEmbedded()) {
          /*
           * Embedded objects are never referred to by uuid in the payload, so an instance will need
           * to be allocated before reading in the properties.
           */
          @SuppressWarnings("unchecked")
          EntityCodex<HasUuid> codex = (EntityCodex<HasUuid>) prop.getCodex();
          HasUuid embedded = codex.allocateEmbedded(payload, context);
          value = ctx.walkImmutable(codex).accept(this, embedded);
        } else {

          @SuppressWarnings("unchecked")
          Codex<Object> codex = (Codex<Object>) prop.getCodex();

          // merchant would become merchantUuid
          String payloadPropertyName = simplePropertyName + codex.getPropertySuffix();

          // Ignore undefined property values, while allowing explicit nullification
          if (!payload.has(payloadPropertyName)) {
            return;
          }

          value = codex.read(payload.get(payloadPropertyName), context);
        }

        if (value == null && prop.getSetter().getParameterTypes()[0].isPrimitive()) {
          // Don't try to pass a null to a primitive setter
          return;
        }

        HasUuid entity = stack.peek().entity;
        Principal principal = context.getPrincipal();

        // Verify the new value may be set
        SecurityTarget target = SecurityTarget.of(entity);
        boolean wasResolved = context.wasResolved(entity);
        boolean mayCreate = security.may(principal, target, CREATE_ACTION);
        boolean mayDelete = security.may(principal, target, DELETE_ACTION);
        boolean mayUpdate = security.may(principal, target, UPDATE_ACTION);
        if (value == null && mayDelete) {
          // OK
        } else if (wasResolved && mayUpdate) {
          // OK
        } else if (!wasResolved && (mayCreate || mayUpdate)) {
          // OK
        } else {
          return;
        }

        // Perhaps set the other side of a OneToMany relationship
        Property impliedPropery = prop.getImpliedProperty();
        if (impliedPropery != null && value != null) {
          // Ensure that any linked property is also mutable
          if (!checkAccess(value, context)) {
            context.addWarning(entity,
                "Ignoring property %s because the inverse relationship (%s) may not be set",
                prop.getName(), impliedPropery.getName());
            return;
          }
          ImpliedPropertySetter setter = impliedPropertySetters.get();
          setter.setLater(impliedPropery, value, entity);
          context.addPostWork(setter);
        }

        // Set the value
        setProperty(prop, entity, value);

        // Record the value as having been set
        context.addModified(entity, prop);
      } catch (Exception e) {
        context.fail(e);
      } finally {
        context.popPath();
      }
    } finally {
      context.popPath();
    }
  }

  @Override
  public <Q extends HasUuid> void endVisit(Q entity, EntityCodex<Q> codex, VisitorContext<Q> ctx) {
    stack.pop();
    context.popPath();
  }

  public void setPayload(JsonObject payload) {
    this.payload = payload;
  }

  /**
   * Don't care about existing property values.
   */
  @Override
  public boolean visit(Property property, VisitorContext<Property> ctx) {
    return false;
  }

  @Override
  public <Q extends HasUuid> boolean visit(Q entity, EntityCodex<Q> codex, VisitorContext<Q> ctx) {
    context.pushPath("." + entity.getUuid());

    PackReader.State state = new State();
    stack.push(state);

    if (payload.entrySet().size() == 1 && payload.has("uuid")) {
      return false;
    }
    if (!context.checkAccess(entity)) {
      return false;
    }

    // Allow the object to see the data that's about to be applied
    for (Method m : codex.getPreUnpackMethods()) {
      try {
        if (m.getParameterTypes().length == 0) {
          m.invoke(entity);
        } else {
          m.invoke(entity, payload);
        }
      } catch (Exception e) {
        context.fail(e);
      }
    }

    // Register PostUnpack methods
    if (!codex.getPostUnpackMethods().isEmpty()) {
      context.addPostWork(new PostUnpackInvoker(entity, codex.getPostUnpackMethods()));
    }

    state.entity = entity;
    return true;
  }

  /**
   * A hook point for custom subtypes to synthesize property values. The default implementation
   * invokes the method returned from {@link Property#getSetter()}.
   * 
   * @param property the property being read
   * @param target the object from which the property is being read
   * @param value the new property value
   * @throws Exception subclasses may delegate error handling to EntityCodex
   */
  protected void setProperty(Property property, HasUuid target, Object value) {
    if (property.getSetter() != null) {
      try {
        property.getSetter().invoke(target, value);
      } catch (Exception e) {
        throw new RuntimeException("Could not set property value", e);
      }
    }
  }

  /**
   * A fan-out to to {@link DeserializationContext#checkAccess(HasUuid)} that will accept
   * collections.
   */
  private boolean checkAccess(Object object, DeserializationContext ctx) {
    if (object instanceof HasUuid) {
      return ctx.checkAccess((HasUuid) object);
    }
    if (object instanceof Iterable) {
      for (Object obj : ((Iterable<?>) object)) {
        if (!checkAccess(obj, ctx)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}