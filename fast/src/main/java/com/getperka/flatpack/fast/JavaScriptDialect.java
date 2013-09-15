package com.getperka.flatpack.fast;

/*
 * #%L
 * FlatPack Automatic Source Tool
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.AttributeRenderer;
import org.stringtemplate.v4.AutoIndentWriter;
import org.stringtemplate.v4.Interpreter;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.misc.ObjectModelAdaptor;
import org.stringtemplate.v4.misc.STNoSuchPropertyException;

import com.getperka.cli.flags.Flag;
import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.client.dto.ApiDescription;
import com.getperka.flatpack.client.dto.EndpointDescription;
import com.getperka.flatpack.client.dto.ParameterDescription;
import com.getperka.flatpack.ext.EntityDescription;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.util.FlatPackCollections;

public class JavaScriptDialect implements Dialect {

  @Flag(tag = "packageName",
      help = "The name of the package that generated sources should belong to",
      defaultValue = "com.getperka.client")
  static String packageName;

  private static final Logger logger = LoggerFactory.getLogger(JavaScriptDialect.class);

  private static final Map<String, String> validationMap = new HashMap<String, String>();

  static {

    validationMap.put("javax.validation.Valid",
        "com.getperka.flatpack.validation.Valid");
    validationMap.put("javax.validation.constraints.AssertFalse",
        "com.getperka.flatpack.validation.AssertFalse");
    validationMap.put("javax.validation.constraints.AssertTrue",
        "com.getperka.flatpack.validation.AssertTrue");
    validationMap.put("javax.validation.constraints.DecimalMax",
        "com.getperka.flatpack.validation.Max");
    validationMap.put("javax.validation.constraints.DecimalMin",
        "com.getperka.flatpack.validation.Min");
    validationMap.put("javax.validation.constraints.Future",
        "com.getperka.flatpack.validation.Future");
    validationMap.put("javax.validation.constraints.Min",
        "com.getperka.flatpack.validation.Min");
    validationMap.put("javax.validation.constraints.Max",
        "com.getperka.flatpack.validation.Max");
    validationMap.put("javax.validation.constraints.NotNull",
        "com.getperka.flatpack.validation.NotNull");
    validationMap.put("javax.validation.constraints.Null",
        "com.getperka.flatpack.validation.Null");
    validationMap.put("javax.validation.constraints.Past",
        "com.getperka.flatpack.validation.Past");
    validationMap.put("javax.validation.constraints.Size",
        "com.getperka.flatpack.validation.Size");

    // validationMap.put("javax.validation.constraints.Digits",
    // "com.getperka.flatpack.validation.");
    // validationMap.put("javax.validation.constraints.Pattern",
    // "com.getperka.flatpack.validation.");
  }

  private static String upcase(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private EntityDescription baseHasUuid;
  private List<String> entityRequires;

  @Override
  public void generate(ApiDescription api, File outputDir) throws IOException {

    if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
      logger.error("Could not create output directory {}", outputDir.getPath());
      return;
    }

    // first collect just our model entities
    Set<String> requires = new HashSet<String>();
    Map<String, EntityDescription> allEntities =
        FlatPackCollections.mapForIteration();

    for (EntityDescription entity : api.getEntities()) {
      addEntity(allEntities, requires, entity);
    }

    // Ensure that the "real" implementations are used
    baseHasUuid = allEntities.remove("baseHasUuid");
    allEntities.remove("hasUuid");
    entityRequires = new ArrayList<String>(requires);
    Collections.sort(entityRequires);

    // Render entities
    STGroup group = loadGroup();
    ST entityST = null;
    for (EntityDescription entity : allEntities.values()) {
      entityST = group.getInstanceOf("entity").add("entity", entity);
      render(entityST, outputDir, camelCaseToUnderscore(entity.getTypeName()) + ".js");
    }

    // render api stubs
    ST apiST = group.getInstanceOf("api").add("api", api);
    render(apiST, outputDir, "generated_base_api.js");
  }

  @Override
  public String getDialectName() {
    return "js";
  }

  /**
   * Adds an entity and its supertypes to a map. The properties defined by the entity will be pruned
   * so that the entity contains only its declared properties.
   * 
   * @param allEntities an accumulator map of entity payload names to descriptions
   * @param entity the entity to add
   */
  protected void addEntity(Map<String, EntityDescription> allEntities,
      Set<String> requires, EntityDescription entity) {

    if (entity == null) {
      return;
    }

    String typeName = entity.getTypeName();

    if (allEntities.containsKey(typeName)) {
      // Already processed
      return;
    } else if ("baseHasUuid".equals(typeName) || "hasUuid".equals(typeName)) {
      // Ensure that the "real" implementations are used
      return;
    }

    allEntities.put(typeName, entity);
    for (Iterator<Property> it = entity.getProperties().iterator(); it.hasNext();) {
      Property prop = it.next();
      if ("uuid".equals(prop.getName())) {
        // Crop the UUID property
        it.remove();
      } else if (!prop.getEnclosingType().equals(entity)) {
        // Remove properties not declared in the current type
        it.remove();
      }
    }

    requires.add(getPackageName(entity) + "." + upcase(entity.getTypeName()));

    // Add the supertype
    addEntity(allEntities, requires, entity.getSupertype());
  }

  private String camelCaseToUnderscore(String s) {
    return s.replaceAll(
        String.format("%s|%s|%s", "(?<=[A-Z])(?=[A-Z][a-z])",
            "(?<=[^A-Z])(?=[A-Z])", "(?<=[A-Za-z])(?=[^A-Za-z])"), "_")
        .toLowerCase();
  }

  private String collectionNameForProperty(Property p) {
    return upcase(p.getType().getListElement().getName()) + "Collection";
  }

  private String getBuilderReturnType(EndpointDescription end) {
    // Convert a path like /api/2/foo/bar/{}/baz to FooBarBazMethod
    String path = end.getPath();
    String[] parts = path.split(Pattern.quote("/"));
    StringBuilder sb = new StringBuilder();
    sb.append(upcase(end.getMethod().toLowerCase()));
    for (int i = 3, j = parts.length; i < j; i++) {
      try {
        String part = parts[i];
        if (part.length() == 0) {
          continue;
        }
        StringBuilder decodedPart = new StringBuilder(URLDecoder
            .decode(part, "UTF8"));
        // Trim characters that aren't legal
        for (int k = decodedPart.length() - 1; k >= 0; k--) {
          if (!Character.isJavaIdentifierPart(decodedPart.charAt(k))) {
            decodedPart.deleteCharAt(k);
          }
        }
        // Append the new name part, using camel-cased names
        String newPart = decodedPart.toString();
        if (sb.length() > 0) {
          newPart = upcase(newPart);
        }
        sb.append(newPart);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    sb.append("Request");

    return packageName + "." + upcase(sb.toString());
  }

  private String getNameForType(Type type) {
    String name = type.getName();
    name = name == null || name.trim().length() == 0 ? type.getJsonKind().name()
        .toLowerCase() : name;
    return name;
  }

  private String getPackageName(EntityDescription entity) {
    return entity.getTypeName().equals("baseHasUuid") ?
        "com.getperka.flatpack.core" : packageName;
  }

  private List<Property> getSortedCollectionProperties(EntityDescription entity) {
    List<Property> properties = new ArrayList<Property>();
    for (Property p : entity.getProperties()) {
      if (p.getType().getListElement() != null &&
        p.getType().getListElement().getName() != null) {
        properties.add(p);
      }
    }
    List<Property> sortedProperties = new ArrayList<Property>();
    sortedProperties.addAll(properties);
    Collections.sort(sortedProperties, new Comparator<Property>() {
      @Override
      public int compare(Property p1, Property p2) {
        return p1.getName().compareTo(p2.getName());
      }
    });
    return sortedProperties;
  }

  private String getValidationParameters(Annotation annotation) {
    String params = "";

    if (annotation instanceof Min) {
      params += ((Min) annotation).value();
    }

    if (annotation instanceof Max) {
      params += ((Max) annotation).value();
    }

    if (annotation instanceof DecimalMin) {
      params += ((DecimalMax) annotation).value();
    }

    if (annotation instanceof DecimalMax) {
      params += ((DecimalMax) annotation).value();
    }

    if (annotation instanceof Size) {
      Size size = (Size) annotation;
      params += size.min() + ", " + size.max();
    }

    return params;
  }

  private boolean hasCustomRequestBuilderClass(EndpointDescription end) {
    return (end.getQueryParameters() != null && !end.getQueryParameters().isEmpty()) ||
      (end.getReturnType() != null && end.getReturnType().getUuid() != null);
  }

  /**
   * Converts the given docString to be jsDoc compatible
   * 
   * @param docString
   * @return
   */
  private String jsDocString(String docString) {
    if (docString == null) return docString;

    String newDocString = docString;
    try {
      // replace <entityReference> tags with /link /endlink pairs
      Pattern regex = Pattern.compile(
          "<entityReference payloadName='([^']*)'>([^<]*)</entityReference>",
          Pattern.CASE_INSENSITIVE);
      Matcher matcher = regex.matcher(docString);
      while (matcher.find()) {
        Type type = new Type.Builder().withName(matcher.group(1).trim()).build();
        String jsType = jsTypeForType(type);

        String link = "{@link " + jsType + "}";
        newDocString = newDocString.replaceAll(matcher.group(0), link);
      }

      // replace #getFoo() method calls to #foo() method calls
      regex = Pattern.compile(
          "#get([^(]*)" + Pattern.quote("()"),
          Pattern.CASE_INSENSITIVE);
      matcher = regex.matcher(docString);
      while (matcher.find()) {
        String newMethod = "#" + matcher.group(1);
        newDocString = newDocString.replaceAll(matcher.group(0), newMethod);
      }
    }

    catch (Exception e) {
      logger.error("Couldn't doxygenize doc string: " + docString, e);
    }
    return newDocString;
  }

  private String jsTypeForType(Type type) {
    if (type.getName() != null) {
      // if the type is an enum, the type will be a string
      if (type.getEnumValues() != null) {
        return "String";
      }
      return packageName + "." + upcase(type.getName());
    }

    if (type.getTypeHint() != null
      && type.getTypeHint().getValue().equals("org.joda.time.DateTime")) {
      return "Date";
    }
    else if (type.getTypeHint() != null
      && type.getTypeHint().getValue().equals("org.joda.time.LocalDateTime")) {
      return "LocalDate";
    }

    String jsType = "nil";
    switch (type.getJsonKind()) {
      case BOOLEAN:
        jsType = "Boolean";
        break;
      case DOUBLE:
        jsType = "Number";
        break;
      case ANY:
        jsType = "Object";
        break;
      case INTEGER:
        jsType = "Number";
        break;
      case LIST:
        if (type.getListElement() != null && (type.getListElement().getEnumValues() != null ||
          type.getListElement().getName() == null)) {
          jsType = "Array";
        } else {
          jsType = "Backbone.Collection";
        }
        break;
      case MAP:
        jsType = "Object";
        break;
      case NULL:
        jsType = "null";
        break;
      case STRING:
        jsType = "String";
        break;
      default:
        break;
    }

    return jsType;
  }

  /**
   * Load {@code js.stg} from the classpath and configure a number of model adaptors to add virtual
   * properties to the objects being rendered.
   */
  private STGroup loadGroup() {

    STGroup group = new STGroupFile(getClass().getResource("js.stg"), "UTF8", '<', '>');
    group.registerRenderer(EntityDescription.class, new AttributeRenderer() {
      @Override
      public String toString(Object o, String formatString, Locale locale) {
        EntityDescription entity = (EntityDescription) o;
        if (entity.getTypeName().equals("baseHasUuid")) {
          return BaseHasUuid.class.getCanonicalName();
        }
        return entity.getTypeName();
      }
    });

    group.registerModelAdaptor(EntityDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {

            EntityDescription entity = (EntityDescription) o;
            if ("docString".equals(propertyName)) {
              return jsDocString(entity.getDocString());
            }

            else if ("canonicalName".equals(propertyName)) {
              return getPackageName(entity) + "." + upcase(entity.getTypeName());
            }

            else if ("supertype".equals(propertyName)) {
              EntityDescription supertype = entity.getSupertype();
              if (supertype == null) {
                supertype = baseHasUuid;
              }
              return supertype;
            }

            else if ("properties".equals(propertyName)) {

              Map<String, Property> propertyMap = new HashMap<String, Property>();
              for (Property p : entity.getProperties()) {
                propertyMap.put(p.getName(), p);
              }

              List<Property> sortedProperties = new ArrayList<Property>();
              sortedProperties.addAll(propertyMap.values());
              Collections.sort(sortedProperties, new Comparator<Property>() {
                @Override
                public int compare(Property p1, Property p2) {
                  return p1.getName().compareTo(p2.getName());
                }
              });

              return sortedProperties;
            }

            else if ("collectionProperties".equals(propertyName)) {
              return getSortedCollectionProperties(entity);
            }

            else if ("uniqueTypeCollectionListProperties".equals(propertyName)) {
              List<Property> props = getSortedCollectionProperties(entity);
              Iterator<Property> iter = props.iterator();

              Set<String> seen = new HashSet<String>();
              while (iter.hasNext()) {
                Property prop = iter.next();
                String name = collectionNameForProperty(prop);
                if (!seen.contains(name)) {
                  seen.add(name);
                }
                else {
                  iter.remove();
                }
              }
              return props;
            }

            else if ("validations".equals(propertyName)) {
              Map<String, List<String>> map = new HashMap<String, List<String>>();

              for (Property p : entity.getProperties()) {
                List<String> validations = new ArrayList<String>();

                List<Annotation> docAnnotations = p.getDocAnnotations();
                if (docAnnotations != null) {
                  for (Annotation a : docAnnotations) {
                    String name = a.annotationType().getName();

                    String validation = validationMap.get(name);
                    if (validation != null) {
                      validation = "new " + validation + "(";
                      validation += getValidationParameters(a);
                      validation += ")";
                      validations.add(validation);
                    }
                  }
                }

                if (!validations.isEmpty()) {
                  map.put(p.getName(), validations);
                }
              }
              return map;
            }

            else if ("validationRequires".equals(propertyName)) {
              Set<String> requires = new HashSet<String>();

              for (Property p : entity.getProperties()) {
                List<Annotation> docAnnotations = p.getDocAnnotations();
                if (docAnnotations != null) {
                  for (Annotation a : docAnnotations) {
                    String name = a.annotationType().getName();
                    String require = validationMap.get(name);
                    if (require != null) {
                      requires.add(require);
                    }
                  }
                }
              }

              return requires;
            }

            return super.getProperty(interp, self, o, property, propertyName);
          }

        });

    group.registerModelAdaptor(Property.class, new ObjectModelAdaptor() {
      @Override
      public Object getProperty(Interpreter interp, ST self, Object o,
          Object property, String propertyName)
          throws STNoSuchPropertyException {
        Property p = (Property) o;
        if ("docString".equals(propertyName)) {
          String docString = jsDocString(p.getDocString());

          List<String> enumValues = p.getType().getEnumValues();
          if (enumValues == null && p.getType().getListElement() != null &&
            p.getType().getListElement().getEnumValues() != null) {
            enumValues = p.getType().getListElement().getEnumValues();
          }
          if (enumValues != null) {
            docString = docString == null ? "" : docString;
            docString += "\n\nPossible values: ";
            for (int i = 0; i < enumValues.size(); i++) {
              docString += enumValues.get(i);
              if (i < enumValues.size() - 1) docString += ", ";
            }
          }

          return docString;

        }

        else if ("jsType".equals(propertyName)) {
          return jsTypeForType(p.getType());
        }

        else if ("listElementEnum".equals(propertyName)) {
          if (p.getType() != null && p.getType().getListElement() != null &&
            p.getType().getListElement().getEnumValues() != null) {
            String enumVals = "";
            int idx = 0;
            for (String val : p.getType().getListElement().getEnumValues()) {
              if (idx != 0) {
                enumVals += ", ";
              }
              enumVals += val;
              idx++;
            }
            return enumVals;
          }
        }

        else if ("listElementKind".equals(propertyName)) {
          if (p.getType().getListElement() != null &&
            p.getType().getListElement().getName() != null) {
            return (packageName + "." + upcase(p.getType().getListElement().toString()));
          }
        }

        else if ("collectionName".equals(propertyName)) {
          return collectionNameForProperty(p);
        }

        else if ("canonicalListElementKind".equals(propertyName)) {
          if (p.getType().getListElement() != null) {

            String collectionModelType = jsTypeForType(p.getType().getListElement());
            Property implied = p.getImpliedProperty();
            if (implied != null) {
              return "function(attrs, options) {\n" +
                "      return new " + collectionModelType + "(\n" +
                "        _(attrs).extend({ " + implied.getName()
                + " : self }), options);\n" +
                "    }";
            }
            else {
              return collectionModelType;
            }
          }
        }

        else if ("defaultValue".equals(propertyName)) {
          String defaultVal = "undefined";
          if (p.isEmbedded()) {
            defaultVal = "new " + jsTypeForType(p.getType()) + "()";
          }
          if (p.getType().getJsonKind().equals(JsonKind.LIST) &&
            jsTypeForType(p.getType().getListElement()).equals("String")) {
            defaultVal = "[]";
            jsTypeForType(p.getType());
          }
          else if (p.getType().getJsonKind().equals(JsonKind.LIST) &&
            p.getType().getListElement().getName() != null) {
            defaultVal = "new " + collectionNameForProperty(p) + "()";
          }
          return defaultVal;
        }

        return super.getProperty(interp, self, o, property, propertyName);
      }
    });

    group.registerModelAdaptor(String.class, new ObjectModelAdaptor() {

      @Override
      public Object getProperty(Interpreter interp, ST self, Object o,
          Object property, String propertyName)
          throws STNoSuchPropertyException {
        final String string = (String) o;
        if ("chunks".equals(propertyName)) {
          /*
           * Split a string into individual chunks that can be reflowed. This implementation is
           * pretty simplistic, but helps make the generated documentation at least somewhat more
           * readable.
           */
          return new Iterator<CharSequence>() {
            int index;
            int length = string.length();
            CharSequence next;

            {
              advance();
            }

            @Override
            public boolean hasNext() {
              return next != null;
            }

            @Override
            public CharSequence next() {
              CharSequence toReturn = next;
              advance();
              return toReturn;
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException();
            }

            private void advance() {
              int start = advance(false);
              int end = advance(true);
              if (start == end) {
                next = null;
              } else {
                next = string.subSequence(start, end);
              }
            }

            /**
             * Advance to next non-whitespace character.
             */
            private int advance(boolean whitespace) {
              while (index < length
                && (whitespace ^ Character.isWhitespace(string.charAt(index)))) {
                index++;
              }
              return index;
            }
          };
        }
        return super.getProperty(interp, self, o, property, propertyName);
      }
    });

    group.registerModelAdaptor(ApiDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {
            ApiDescription apiDescription = (ApiDescription) o;
            if ("endpoints".equals(propertyName)) {
              Set<EndpointDescription> uniqueEndpoints =
                  new HashSet<EndpointDescription>(apiDescription.getEndpoints());
              List<EndpointDescription> sortedEndpoints = new ArrayList<EndpointDescription>(
                  uniqueEndpoints);
              Collections.sort(sortedEndpoints, new Comparator<EndpointDescription>() {
                @Override
                public int compare(EndpointDescription e1, EndpointDescription e2) {
                  return e1.getPath().compareTo(e2.getPath());
                }
              });
              Iterator<EndpointDescription> iter = sortedEndpoints.iterator();
              while (iter.hasNext()) {
                EndpointDescription ed = iter.next();
                if (ed.getPath() != null && ed.getPath().contains("{path:.*}")) {
                  iter.remove();
                }
              }
              return sortedEndpoints;
            }
            else if ("flatpackEndpoints".equals(propertyName)) {
              List<EndpointDescription> sortedEndpoints = new ArrayList<EndpointDescription>(
                  apiDescription.getEndpoints());
              Collections.sort(sortedEndpoints, new Comparator<EndpointDescription>() {
                @Override
                public int compare(EndpointDescription e1, EndpointDescription e2) {
                  return e1.getPath().compareTo(e2.getPath());
                }
              });
              Iterator<EndpointDescription> iter = sortedEndpoints.iterator();
              while (iter.hasNext()) {
                if (!hasCustomRequestBuilderClass(iter.next())) {
                  iter.remove();
                }
              }
              return sortedEndpoints;
            }
            else if ("requireNames".equals(propertyName)) {
              return entityRequires;
            }
            return super.getProperty(interp, self, o, property, propertyName);
          }
        });

    group.registerModelAdaptor(EndpointDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {
            EndpointDescription end = (EndpointDescription) o;
            if ("docString".equals(propertyName)) {
              return jsDocString(end.getDocString());
            }
            if ("methodName".equals(propertyName)) {

              String path = end.getPath();
              String[] parts = path.split(Pattern.quote("/"));
              StringBuilder sb = new StringBuilder();
              sb.append(end.getMethod().toLowerCase());
              for (int i = 3, j = parts.length; i < j; i++) {
                String part = parts[i];
                if (part.length() == 0) continue;
                if (!part.startsWith("{") && !part.endsWith("}")) {
                  if (part.contains(".")) {
                    String[] dotPart = part.split(Pattern.quote("."));
                    for (String dot : dotPart) {
                      sb.append(upcase(dot));
                    }
                  }
                  else {
                    sb.append(upcase(part));
                  }
                }
                else {
                  sb.append(upcase(part.substring(1, part.length() - 1)));
                }
              }

              return sb.toString();
            }

            else if ("methodParameterList".equals(propertyName)) {
              String path = end.getPath();
              String[] parts = path.split(Pattern.quote("/"));
              StringBuilder sb = new StringBuilder();
              int paramCount = 0;
              for (int i = 3, j = parts.length; i < j; i++) {
                String part = parts[i];
                if (part.length() == 0) continue;

                if (part.startsWith("{") && part.endsWith("}")) {
                  String name = part.substring(1, part.length() - 1);
                  sb.append(name);
                  paramCount++;
                  if (end.getPathParameters() != null
                    && paramCount < end.getPathParameters().size()) {
                    sb.append(", ");
                  }
                }
              }
              if (end.getEntity() != null) {
                if (paramCount > 0) {
                  sb.append(", ");
                }
                sb.append(getNameForType(end.getEntity()));
              }

              return sb.toString();
            }
            else if ("entityName".equals(propertyName)) {
              return getNameForType(end.getEntity());
            }
            else if ("requestBuilderClassName".equals(propertyName)) {

              if (hasCustomRequestBuilderClass(end)) {
                return getBuilderReturnType(end);
              }
              else {
                return "com.getperka.flatpack.client.JsonRequest";
              }
            }

            else if ("requestBuilderBlockName".equals(propertyName)) {
              return getBuilderReturnType(end) + "Block";
            }

            else if ("pathDecoded".equals(propertyName)) {
              // URL-decode the path in the endpoint description
              try {
                String decoded = URLDecoder.decode(end.getPath(), "UTF8");
                return decoded;
              } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
              }
            }
            return super.getProperty(interp, self, o, property, propertyName);
          }

        });

    group.registerModelAdaptor(ParameterDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {
            ParameterDescription param = (ParameterDescription) o;
            if ("requireName".equals(propertyName)) {
              return upcase(param.getName());
            }
            else if ("docString".equals(propertyName)) {
              return jsDocString(param.getDocString());
            }
            return super.getProperty(interp, self, o, property, propertyName);
          }
        });

    Map<String, Object> namesMap = new HashMap<String, Object>();
    namesMap.put("packageName", packageName);
    group.defineDictionary("names", namesMap);

    return group;
  }

  private void render(ST enumST, File packageDir, String fileName)
      throws IOException {

    if (!packageDir.isDirectory() && !packageDir.mkdirs()) {
      logger
          .error("Could not create output directory {}", packageDir.getPath());
      return;
    }
    Writer fileWriter = new OutputStreamWriter(new FileOutputStream(new File(
        packageDir, fileName)), "UTF8");
    AutoIndentWriter writer = new AutoIndentWriter(fileWriter);
    writer.setLineWidth(80);
    enumST.write(writer);
    fileWriter.close();
  }
}
