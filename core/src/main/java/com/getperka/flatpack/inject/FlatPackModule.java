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
package com.getperka.flatpack.inject;

import java.security.Principal;
import java.util.Collection;

import org.joda.time.DateTime;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.getperka.flatpack.Configuration;
import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.PersistenceMapper;
import com.getperka.flatpack.TraversalMode;
import com.getperka.flatpack.codexes.DefaultCodexMapper;
import com.getperka.flatpack.ext.CodexMapper;
import com.getperka.flatpack.ext.EntityResolver;
import com.getperka.flatpack.security.MemoizingSecurity;
import com.getperka.flatpack.security.NoSecurity;
import com.getperka.flatpack.security.PermissivePrincipalMapper;
import com.getperka.flatpack.security.PrincipalMapper;
import com.getperka.flatpack.security.PrincipalSecurity;
import com.getperka.flatpack.security.ReflexiveSecurityPolicy;
import com.getperka.flatpack.security.Security;
import com.getperka.flatpack.security.SecurityPolicy;
import com.getperka.flatpack.util.IoObserver;
import com.google.gson.stream.JsonWriter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;

public class FlatPackModule extends AbstractModule {
  private final Configuration configuration;

  public FlatPackModule(Configuration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {
    // Allow spying on IO
    bind(IoObserver.class)
        .to(configuration.isVerbose() ? IoObserver.Verbose.class : IoObserver.Null.class);

    // Bind simple constants
    bindConstant()
        .annotatedWith(IgnoreUnresolvableTypes.class)
        .to(configuration.isIgnoreUnresolvableTypes());
    bindConstant()
        .annotatedWith(PrettyPrint.class)
        .to(configuration.isPrettyPrint());
    bindConstant()
        .annotatedWith(Verbose.class)
        .to(configuration.isVerbose());
    bindConstant()
        .annotatedWith(VerboseLogChunkSize.class)
        .to(configuration.getVerboseLogChunkSize());

    // Provide all class types
    bind(new TypeLiteral<Collection<Class<?>>>() {})
        .annotatedWith(AllTypes.class)
        .toInstance(configuration.getAllTypes());

    bindImplementationTypes();
    bindPackScope();
    bindUserTypes();
  }

  @Provides
  @FlatPackLogger
  @Singleton
  protected Logger logger(@FlatPackLogger ILoggerFactory factory) {
    return factory.getLogger(FlatPack.class.getName());
  }

  @Provides
  @FlatPackLogger
  @Singleton
  protected ILoggerFactory loggerFactory() {
    return LoggerFactory.getILoggerFactory();
  }

  /**
   * Provide explicit bindings for implementation types that will require access to the private
   * Injector (because they create types dynamically).
   */
  private void bindImplementationTypes() {
    bind(DefaultCodexMapper.class);
  }

  /**
   * Set up bindings for {@link PackScoped} types (e.g. SerializationContext).
   */
  private void bindPackScope() {
    PackScope packScope = new PackScope();
    // Make the instance of the PackScope available
    bind(PackScope.class).toInstance(packScope);

    // All @PackScoped object should be constructed through the scope
    bindScope(PackScoped.class, packScope);

    // Always provide a binding for Principal
    bind(Principal.class)
        .to(NullPrincipal.class)
        .in(packScope);

    // Additional, scope-specific bindings
    bind(DateTime.class)
        .annotatedWith(LastModifiedTime.class)
        .toProvider(Providers.of(new DateTime(0)))
        .in(packScope);

    bind(JsonWriter.class)
        .toProvider(PackScope.<JsonWriter> provider())
        .in(packScope);

    bind(MemoizingSecurity.class).in(packScope);

    bind(TraversalMode.class)
        .toProvider(PackScope.<TraversalMode> provider())
        .in(packScope);
  }

  /**
   * Attach bindings for user-injectable behaviors or default instances.
   */
  private void bindUserTypes() {
    // CodexMapper
    if (configuration.getExtraMappers().isEmpty()) {
      bind(CodexMapper.class)
          .to(DefaultCodexMapper.class);
    } else {
      bind(CodexMapper.class)
          .toInstance(
              new CompositeCodexMapper(getProvider(DefaultCodexMapper.class),
                  configuration.getExtraMappers()));
    }

    // EntityResolver
    if (configuration.getEntityResolvers().size() == 1) {
      bind(EntityResolver.class)
          .toInstance(configuration.getEntityResolvers().get(0));
    } else {
      bind(EntityResolver.class)
          .toInstance(new CompositeEntityResolver(configuration.getEntityResolvers()));
    }

    // PersistenceMapper
    if (configuration.getPersistenceMappers().size() == 1) {
      bind(PersistenceMapper.class).toInstance(configuration.getPersistenceMappers().get(0));
    } else {
      bind(PersistenceMapper.class)
          .toInstance(new CompositePersistenceMapper(configuration.getPersistenceMappers()));
    }

    // PrincipalMapper
    if (configuration.getPrincipalMapper() == null) {
      bind(PrincipalMapper.class).to(PermissivePrincipalMapper.class);
      bind(SecurityPolicy.class).to(NoSecurity.class);
    } else {
      bind(PrincipalMapper.class).toInstance(configuration.getPrincipalMapper());

      // SecurityPolicy
      if (configuration.getSecurityPolicy() == null) {
        bind(SecurityPolicy.class).to(ReflexiveSecurityPolicy.class);
      } else {
        bind(SecurityPolicy.class).toInstance(configuration.getSecurityPolicy());
      }
    }

    // Security
    bind(Security.class).to(PrincipalSecurity.class);

  }
}
