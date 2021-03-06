/*
 * #%L
 * FlatPack Jersey integration
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
package com.getperka.flatpack.jersey;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForLookup;
import static com.getperka.flatpack.util.FlatPackCollections.setForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.setForLookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.UriBuilder;

import com.getperka.flatpack.FlatPack;
import com.getperka.flatpack.FlatPackEntity;
import com.getperka.flatpack.HasUuid;
import com.getperka.flatpack.Packer;
import com.getperka.flatpack.TraversalMode;
import com.getperka.flatpack.TypeReference;
import com.getperka.flatpack.Unpacker;
import com.getperka.flatpack.Visitors;
import com.getperka.flatpack.client.dto.ApiDescription;
import com.getperka.flatpack.client.dto.EndpointDescription;
import com.getperka.flatpack.client.dto.ParameterDescription;
import com.getperka.flatpack.client.dto.TypeDescription;
import com.getperka.flatpack.ext.Codex;
import com.getperka.flatpack.ext.EntityDescription;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.ext.TypeContext;
import com.getperka.flatpack.ext.VisitorContext;
import com.getperka.flatpack.inject.HasInjector;
import com.getperka.flatpack.jersey.FlatPackResponse.ExtraEntity;
import com.getperka.flatpack.security.GroupPermissions;
import com.getperka.flatpack.security.SecurityAction;
import com.getperka.flatpack.security.SecurityGroup;
import com.getperka.flatpack.security.SecurityGroups;
import com.getperka.flatpack.util.AcyclicVisitor;
import com.getperka.flatpack.util.FlatPackTypes;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * Analyzes a FlatPack instance and a API methods to produce an {@link ApiDescription}.
 */
public class ApiDescriber {
  private static final Pattern linkPattern =
      Pattern.compile("[{]@link[\\s]+([^\\s}]+)([^}]*)?[}]");

  private String apiName = "API";
  private final Collection<Method> apiMethods;
  @Inject
  private TypeContext ctx;
  private final Map<Package, Map<String, String>> docStringsByPackage = mapForLookup();
  private final Set<Class<? extends HasUuid>> described = setForLookup();
  private Set<Class<? extends HasUuid>> entitiesToExtract = setForIteration();
  private Set<String> limitGroupNames;
  @Inject
  private Packer packer;
  private final Map<Property, EntityDescription> propertiesToEntities = mapForLookup();
  @Inject
  private SecurityGroups securityGroups;
  /**
   * Each entry contains the key and all of its known subtypes.
   */
  private final Map<Class<? extends HasUuid>, Set<Class<? extends HasUuid>>> typeHierarchy = mapForLookup();
  @Inject
  private Unpacker unpacker;
  @Inject
  private Visitors visitors;
  /**
   * Allows weak entity types to be included.
   */
  private Set<EntityDescription> weakEntities = setForLookup();
  /**
   * Entities that contain only these properties will be filtered.
   */
  private Set<Property> weakProperties = setForIteration();

  public ApiDescriber(FlatPack flatpack, Collection<Method> apiMethods) {
    this.apiMethods = apiMethods;
    ((HasInjector) flatpack).getInjector().injectMembers(this);
  }

  /**
   * Analyze the Methods provided to the constructor and produce an ApiDescription.
   */
  public ApiDescription describe() throws IOException {
    // Compute type hierarchy so a reference to a base type will include its subtypes
    for (EntityDescription entity : ctx.getEntityDescriptions()) {
      // Accumulate subtypes as we ascend the type hiererchy
      List<Class<? extends HasUuid>> chain = listForAny();
      while (entity != null) {
        Class<? extends HasUuid> clazz = entity.getEntityType();
        chain.add(clazz);

        Set<Class<? extends HasUuid>> set = typeHierarchy.get(clazz);
        if (set == null) {
          set = setForIteration();
          typeHierarchy.put(clazz, set);
        }

        // Add all accumulated subtypes
        set.addAll(chain);
        entity = entity.getSupertype();
      }
    }

    ApiDescription description = new ApiDescription();
    description.setApiName(apiName);

    List<EntityDescription> entities = new ArrayList<EntityDescription>();
    description.setEntities(entities);

    // Extract API endpoints
    Set<EndpointDescription> endpoints = new LinkedHashSet<EndpointDescription>();

    for (Method method : apiMethods) {
      EndpointDescription desc = describeEndpoint(method);
      if (desc != null) {
        endpoints.add(desc);
      }
    }
    description.setEndpoints(new ArrayList<EndpointDescription>(endpoints));

    // Extract all entities
    Set<Class<?>> seen = setForLookup();
    do {
      Set<Class<? extends HasUuid>> toProcess = entitiesToExtract;
      entitiesToExtract = setForIteration();
      for (Class<? extends HasUuid> clazz : toProcess) {
        if (seen.add(clazz)) {
          entities.add(describeEntity(clazz));
        }
      }
    } while (!entitiesToExtract.isEmpty());

    if (limitGroupNames != null) {
      /*
       * Filter the description, but first we need to make a mutable copy of the internal
       * datastructures.
       */
      JsonElement elt = packer.pack(FlatPackEntity.entity(description));
      description = unpacker.<ApiDescription> unpack(ApiDescription.class, elt, null).getValue();

      visitors.visit(new AcyclicVisitor() {
        @Override
        public <T> void endVisitValue(T value, Codex<T> codex, VisitorContext<T> ctx) {
          boolean keep;
          if (value instanceof Property) {
            Property p = (Property) value;
            keep = shouldKeep(p.getGroupPermissions());

            /*
             * If the implied property should be kept, also keep this one. This allows collection
             * properties in parent types to be generated if only the child's parent-referencing
             * property is visible.
             */
            if (!keep && p.getImpliedProperty() != null) {
              keep = shouldKeep(p.getImpliedProperty().getGroupPermissions());
            }
          } else if (value instanceof EntityDescription) {
            EntityDescription e = (EntityDescription) value;
            if (e.getProperties().isEmpty()) {
              keep = false;
            } else if (!weakEntities.contains(e)
              && weakProperties.containsAll(e.getProperties())) {
              keep = false;
            } else {
              keep = true;
            }
          } else {
            keep = true;
          }

          if (keep) {
            return;
          }

          if (ctx.canRemove()) {
            ctx.remove();
          } else if (ctx.canReplace()) {
            ctx.replace(null);
          } else {
            throw new UnsupportedOperationException("Could not filter");
          }
        }

        private boolean shouldKeep(GroupPermissions permissions) {
          if (permissions == null) {
            return true;
          }
          for (Map.Entry<SecurityGroup, Set<SecurityAction>> entry : permissions.getOperations()
              .entrySet()) {
            // If no actions are granted, ignore the group
            if (entry.getValue().isEmpty()) {
              continue;
            }
            SecurityGroup group = entry.getKey();
            if (securityGroups.getGroupAll().equals(group)
              || securityGroups.getGroupReflexive().equals(group)
              || limitGroupNames.contains(group.getName())) {
              return true;
            }
          }
          return false;
        }
      }, description);
    }

    return description;
  }

  /**
   * Filter entities that contain only properties defined by the given class.
   */
  public ApiDescriber ignoreEmptySubtypes(Class<? extends HasUuid> toIgnore) {
    EntityDescription describe = ctx.describe(toIgnore);
    weakEntities.add(describe);
    weakProperties.addAll(describe.getProperties());
    return this;
  }

  /**
   * Only extract items that may be accessed by {@link SecurityGroup} with the given names.
   */
  public ApiDescriber limitGroupNames(Collection<String> limitRoles) {
    this.limitGroupNames = setForIteration();
    this.limitGroupNames.addAll(limitRoles);
    return this;
  }

  public ApiDescriber withApiName(String apiName) {
    this.apiName = apiName;
    return this;
  }

  protected String keyForType(java.lang.reflect.Type t) {
    if (t instanceof Class) {
      return ((Class<?>) t).getName();
    }

    if (t instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) t;
      StringBuilder sb = new StringBuilder();
      sb.append(keyForType(pt.getRawType())).append("<");
      boolean needsComma = false;
      for (java.lang.reflect.Type param : pt.getActualTypeArguments()) {
        if (needsComma) {
          sb.append(",");
        } else {
          needsComma = true;
        }
        sb.append(keyForType(param));
      }
      sb.append(">");
      return sb.toString();
    }

    throw new UnsupportedOperationException(t.getClass().getName());
  }

  protected String methodKey(Class<?> declaringClass, Method method) {
    String methodKey;
    {
      StringBuilder methodKeyBuilder = new StringBuilder(declaringClass.getName())
          .append(":").append(method.getName()).append("(");
      boolean needsComma = false;
      for (java.lang.reflect.Type paramType : method.getGenericParameterTypes()) {
        if (needsComma) {
          methodKeyBuilder.append(", ");
        } else {
          needsComma = true;
        }
        methodKeyBuilder.append(keyForType(paramType));
      }
      methodKeyBuilder.append(")");
      methodKey = methodKeyBuilder.toString();
    }
    return methodKey;
  }

  private EndpointDescription describeEndpoint(Method method) throws IOException {
    Class<?> declaringClass = method.getDeclaringClass();

    // Determine the HTTP access method
    String methodName = null;
    for (Annotation annotation : method.getAnnotations()) {
      // The HTTP method is declared as a meta-annotation on the @GET, @PUT, etc. annotation
      HttpMethod methodAnnotation = annotation.annotationType().getAnnotation(HttpMethod.class);
      if (methodAnnotation != null) {
        methodName = methodAnnotation.value();
      }
    }
    if (methodName == null) {
      return null;
    }

    // Create a key for looking up the method's doc strings
    String methodKey = methodKey(declaringClass, method);

    // Determine the endpoint path
    UriBuilder builder = UriBuilder.fromPath("");
    if (declaringClass.isAnnotationPresent(Path.class)) {
      builder.path(declaringClass);
    }
    if (method.isAnnotationPresent(Path.class)) {
      builder.path(method);
    }
    // This path has special characters URL-escaped, so we'll undo the escaping
    String path = builder.build().toString();
    path = URLDecoder.decode(path, "UTF8");

    // Build the EndpointDescription
    EndpointDescription desc = new EndpointDescription(methodName, path);
    List<ParameterDescription> pathParams = new ArrayList<ParameterDescription>();
    List<ParameterDescription> queryParams = new ArrayList<ParameterDescription>();
    Annotation[][] annotations = method.getParameterAnnotations();
    java.lang.reflect.Type[] parameters = method.getGenericParameterTypes();
    for (int i = 0, j = parameters.length; i < j; i++) {
      Type paramType = reference(parameters[i]);
      if (annotations[i].length == 0) {
        // Assume that an un-annotated parameter is the main entity type
        desc.setEntity(paramType);
      } else {
        for (Annotation annotation : annotations[i]) {
          if (PathParam.class.equals(annotation.annotationType())) {
            PathParam pathParam = (PathParam) annotation;
            ParameterDescription param = new ParameterDescription(desc, pathParam.value(),
                paramType);
            String docString = getDocStrings(declaringClass).get(methodKey + "[" + i + "]");
            param.setDocString(replaceLinks(docString));
            pathParams.add(param);
          } else if (QueryParam.class.equals(annotation.annotationType())) {
            QueryParam queryParam = (QueryParam) annotation;
            ParameterDescription param = new ParameterDescription(desc, queryParam.value(),
                paramType);
            String docString = getDocStrings(declaringClass).get(methodKey + "[" + i + "]");
            param.setDocString(replaceLinks(docString));
            queryParams.add(param);
          }
        }
      }
    }

    // If the returned entity type is described, extract the information
    FlatPackResponse responseAnnotation = method.getAnnotation(FlatPackResponse.class);
    if (responseAnnotation != null) {
      java.lang.reflect.Type reflectType = FlatPackTypes.createType(responseAnnotation.value());
      Type returnType = reference(reflectType);
      desc.setReturnDocString(
          responseAnnotation.description().isEmpty() ? null : responseAnnotation.description());
      desc.setReturnType(returnType);
      desc.setTraversalMode(responseAnnotation.traversalMode());

      List<TypeDescription> extraTypeDescriptions = new ArrayList<TypeDescription>();
      for (ExtraEntity extra : responseAnnotation.extra()) {
        TypeDescription typeDescription = new TypeDescription();
        typeDescription.setDocString(extra.description());
        typeDescription.setType(reference(FlatPackTypes.createType(extra.type())));
        extraTypeDescriptions.add(typeDescription);
      }
      desc.setExtraReturnData(extraTypeDescriptions.isEmpty() ? null : extraTypeDescriptions);
    } else if (HasUuid.class.isAssignableFrom(method.getReturnType())) {
      Type returnType = reference(method.getReturnType());
      desc.setReturnType(returnType);
      desc.setTraversalMode(TraversalMode.SIMPLE);
    }

    String docString = getDocStrings(declaringClass).get(methodKey);
    desc.setDocString(replaceLinks(docString));
    desc.setPathParameters(pathParams.isEmpty() ? null : pathParams);
    desc.setQueryParameters(queryParams.isEmpty() ? null : queryParams);
    return desc;
  }

  private EntityDescription describeEntity(Class<? extends HasUuid> clazz) throws IOException {
    EntityDescription toReturn = ctx.describe(clazz);
    if (!described.add(clazz)) {
      return toReturn;
    }

    // Attach interesting annotations
    toReturn.setDocAnnotations(extractInterestingAnnotations(clazz));

    // Attach the docstring
    Map<String, String> strings = getDocStrings(clazz);
    String docString = strings.get(clazz.getName());
    if (docString != null) {
      toReturn.setDocString(replaceLinks(docString));
    }

    // Iterate over the properties
    for (Iterator<Property> it = toReturn.getProperties().iterator(); it.hasNext();) {
      Property prop = it.next();
      propertiesToEntities.put(prop, toReturn);

      // Record a reference to (possibly) an entity type
      reference(prop.getType());

      Method accessor = prop.getGetter();
      if (accessor == null) {
        accessor = prop.getSetter();
      }

      // Send down interesting annotations
      prop.setDocAnnotations(extractInterestingAnnotations(accessor));

      // The property set include all properties defined in supertypes
      Class<?> declaringClass = accessor.getDeclaringClass();
      strings = getDocStrings(declaringClass);
      if (strings != null) {
        String memberName = declaringClass.getName() + ":" + accessor.getName() + "()";
        prop.setDocString(replaceLinks(strings.get(memberName)));
      }
    }
    return toReturn;
  }

  private List<Annotation> extractInterestingAnnotations(AnnotatedElement elt) {
    List<Annotation> toReturn = listForAny();

    for (Annotation a : elt.getAnnotations()) {
      if (a.annotationType().equals(Deprecated.class)) {
        toReturn.add(a);
        continue;
      }

      if (a.annotationType().getName().equals("javax.validation.Valid")) {
        toReturn.add(a);
      }

      // Look for JSR-303 constraints
      for (Annotation meta : a.annotationType().getAnnotations()) {
        if (meta.annotationType().getName().equals("javax.validation.Constraint")) {
          toReturn.add(a);
        }
      }
    }

    return toReturn.isEmpty() ? Collections.<Annotation> emptyList() : toReturn;
  }

  /**
   * Load the {@code package.json} file from the class's package.
   */
  private Map<String, String> getDocStrings(Class<?> clazz) throws IOException {
    Map<String, String> toReturn = docStringsByPackage.get(clazz.getPackage());
    if (toReturn != null) {
      return toReturn;
    }

    InputStream stream = clazz.getResourceAsStream("package.json");
    if (stream == null) {
      toReturn = Collections.emptyMap();
    } else {
      Reader reader = new InputStreamReader(stream, FlatPackTypes.UTF8);
      toReturn = new Gson().fromJson(reader, new TypeReference<Map<String, String>>() {}.getType());
      reader.close();
    }

    docStringsByPackage.put(clazz.getPackage(), toReturn);
    return toReturn;
  }

  private void reference(Class<? extends HasUuid> clazz) {
    if (clazz != null) {
      entitiesToExtract.addAll(typeHierarchy.get(clazz));
    }
  }

  /**
   * Convert a reflection Type into FlatPack's typesystem. This method will also record any
   * referenced entities.
   */
  private Type reference(java.lang.reflect.Type t) {
    // If t is a FlatPackEntity<Foo>, return a description of Foo
    java.lang.reflect.Type referencedEntityType =
        FlatPackTypes.getSingleParameterization(t, FlatPackEntity.class);
    if (referencedEntityType != null) {
      t = referencedEntityType;
    }
    // Ensure that the TypeContext has processed the type
    if (t instanceof Class<?> && HasUuid.class.isAssignableFrom((Class<?>) t)) {
      ctx.describe(((Class<?>) t).asSubclass(HasUuid.class));
    }
    Type type = ctx.getCodex(t).describe();
    reference(type);
    return type;
  }

  /**
   * Traverse a type, looking for references to entities. This should be a visitor.
   */
  private void reference(Type type) {
    if (type.getName() != null && type.getEnumValues() == null) {
      EntityDescription description = ctx.getEntityDescription(type.getName());
      Class<? extends HasUuid> clazz = description.getEntityType();
      reference(clazz);
    }
    if (type.getListElement() != null) {
      reference(type.getListElement());
    }
    if (type.getMapKey() != null) {
      reference(type.getMapKey());
    }
    if (type.getMapValue() != null) {
      reference(type.getMapValue());
    }
  }

  /**
   * Replace any {@literal {@link} tags in a docString with something easier for the viewer app to
   * deal with.
   */
  private String replaceLinks(String docString) {
    if (docString == null) {
      return null;
    }

    // Matcher uses StringBuffer and not StringBuilder
    StringBuffer sb = new StringBuffer();
    Matcher m = linkPattern.matcher(docString);
    while (m.find()) {
      String name = m.group(1);
      // TODO: Support field references, API method references
      EntityDescription referenced = ctx.getEntityDescription(name);
      if (referenced == null) {
        // Just append the original text
        if (m.group(2) != null) {
          m.appendReplacement(sb, m.group(2));
        } else {
          m.appendReplacement(sb, m.group(1));
        }
      } else {
        /*
         * This is colluding with the viewer app, but it's much simpler than re-implementing another
         * {@link} replacement in the viewer.
         */
        String payloadName = referenced.getTypeName();
        String displayString = m.group(2) == null ? payloadName : m.group(2);
        m.appendReplacement(sb, "<entityReference payloadName='" + payloadName + "'>"
          + displayString + "</entityReference>");
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
