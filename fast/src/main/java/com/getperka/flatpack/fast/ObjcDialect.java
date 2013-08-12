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

import static org.jvnet.inflector.Noun.pluralOf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.getperka.flatpack.client.dto.EntityDescription;
import com.getperka.flatpack.client.dto.ParameterDescription;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.util.FlatPackCollections;

public class ObjcDialect implements Dialect {

  @Flag(tag = "classPrefix",
      help = "The prefix to add to all class names",
      defaultValue = "FP")
  static String classPrefix;

  private static final List<String> KEYWORDS = Arrays.asList(
      "void",
      "char",
      "short",
      "int",
      "long",
      "float",
      "double",
      "signed",
      "unsigned",
      "id",
      "const",
      "volatile",
      "in",
      "out",
      "inout",
      "bycopy",
      "byref",
      "oneway",
      "self",
      "super");

  private static final Logger logger = LoggerFactory.getLogger(ObjcDialect.class);

  private static String downcase(String s) {
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  private static String upcase(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @Override
  public void generate(ApiDescription api, File outputDir) throws IOException {
    if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
      logger.error("Could not create output directory {}", outputDir.getPath());
      return;
    }

    // first collect just our model entities
    Map<String, EntityDescription> allEntities = FlatPackCollections
        .mapForIteration();
    for (EntityDescription entity : api.getEntities()) {
      allEntities.put(entity.getTypeName(), entity);
      for (Iterator<Property> it = entity.getProperties().iterator(); it.hasNext();) {
        Property prop = it.next();
        // Remove the uuid property
        if ("uuid".equals(prop.getName())) {
          it.remove();
        }

        // and properties not declared in the current type
        else if (!prop.getEnclosingTypeName().equals(entity.getTypeName())) {
          it.remove();
        }
      }
    }
    // Ensure that the "real" implementations are used
    allEntities.remove("baseHasUuid");
    allEntities.remove("hasUuid");

    // Render entities
    STGroup group = loadGroup();
    ST entityST = null;
    for (EntityDescription entity : allEntities.values()) {
      entityST = group.getInstanceOf("entityHeader").add("entity", entity);
      render(entityST, outputDir, upcase(entity.getTypeName()) + ".h");

      entityST = group.getInstanceOf("entity").add("entity", entity);
      render(entityST, outputDir, upcase(entity.getTypeName()) + ".m");
    }

    // render api stubs
    ST apiHeaderST = group.getInstanceOf("apiHeader").add("api", api);
    render(apiHeaderST, outputDir, "BaseApi.h");
    ST apiST = group.getInstanceOf("api").add("api", api);
    render(apiST, outputDir, "BaseApi.m");

    // render schema definition
    ST schemaHeaderST = group.getInstanceOf("schemaHeader");
    render(schemaHeaderST, outputDir, "Schema.h");
    ST schemaST = group.getInstanceOf("schema").add("entities", allEntities.values());
    render(schemaST, outputDir, "Schema.m");
  }

  @Override
  public String getDialectName() {
    return "objc";
  }

  /**
   * Converts the given docString to be doxygen compatible
   * 
   * @param docString
   * @return
   */
  private String doxygenDocString(String docString) {
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
        String objcType = objcTypeForType(type);

        String link = "\\\\link " + objcType + " " +
          matcher.group(2).trim() + " \\\\endlink";
        newDocString = newDocString.replaceAll(matcher.group(0), link);
      }

      // replace #getFoo() method calls to #foo() method calls
      regex = Pattern.compile(
          "#get([^(]*)" + Pattern.quote("()"),
          Pattern.CASE_INSENSITIVE);
      matcher = regex.matcher(docString);
      while (matcher.find()) {
        String newMethod = "#" + downcase(matcher.group(1));
        newDocString = newDocString.replaceAll(matcher.group(0), newMethod);
      }
    }

    catch (Exception e) {
      logger.error("Couldn't doxygenize doc string: " + docString, e);
    }
    return newDocString;
  }

  private List<Property> filterProperties(EntityDescription entity, boolean includeRelationships) {
    List<Property> props = getSortedProperties(entity);
    Iterator<Property> iter = props.iterator();
    while (iter.hasNext()) {
      Property prop = iter.next();
      boolean isRelationship = prop.getType().getName() != null ||
        prop.getType().getListElement() != null;
      if (isRelationship ^ includeRelationships) {
        iter.remove();
      }
    }
    return props;
  }

  private String getBuilderReturnType(EndpointDescription end) {

    // Convert a path like /api/2/foo/bar/{}/baz to FooBarBazMethod
    String path = end.getPath();
    String[] parts = path.split(Pattern.quote("/"));
    StringBuilder sb = new StringBuilder();
    sb.append(classPrefix);
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

    return upcase(sb.toString());
  }

  private String getMethodizedPath(EndpointDescription end) {
    String path = end.getPath();
    String[] parts = path.split(Pattern.quote("/"));
    StringBuilder sb = new StringBuilder();
    sb.append(end.getMethod().toLowerCase());
    int paramCount = 0;
    for (int i = 3, j = parts.length; i < j; i++) {
      String part = parts[i];
      if (part.length() == 0) continue;

      if (part.startsWith("{") && part.endsWith("}")) {
        String name = part.substring(1, part.length() - 1);
        sb.append(paramCount > 0 ? name : upcase(name));
        sb.append(":(NSString *)" + name);
        if (i < parts.length - 1) {
          sb.append(" ");
        }
        paramCount++;
      }

      else {
        sb.append(upcase(part));
      }
    }

    return sb.toString();
  }

  private String getSafeName(String name) {
    return name + (KEYWORDS.contains(name) ? "Property" : "");
  }

  private List<Property> getSortedProperties(EntityDescription entity) {
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

  private boolean isRequiredImport(String type) {
    return type != null && !type.equalsIgnoreCase("nil") && !type.startsWith("NS");
  }

  /**
   * Load {@code objc.stg} from the classpath and configure a number of model adaptors to add
   * virtual properties to the objects being rendered.
   */
  private STGroup loadGroup() {

    STGroup group = new STGroupFile(getClass().getResource("objc.stg"), "UTF8", '<', '>');
    // EntityDescription are rendered as the FQN
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

    group.registerModelAdaptor(ApiDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {
            ApiDescription apiDescription = (ApiDescription) o;
            if ("endpoints".equals(propertyName)) {
              List<EndpointDescription> sortedEndpoints = new ArrayList<EndpointDescription>(
                  apiDescription.getEndpoints());
              Collections.sort(sortedEndpoints, new Comparator<EndpointDescription>() {
                @Override
                public int compare(EndpointDescription e1, EndpointDescription e2) {
                  return e1.getPath().compareTo(e2.getPath());
                }
              });
              return sortedEndpoints;
            }
            else if ("importNames".equals(propertyName)) {
              Set<String> imports = new HashSet<String>();
              for (EndpointDescription e : apiDescription.getEndpoints()) {
                if (e.getEntity() != null) {
                  String type = objcTypeForType(e.getEntity());
                  if (isRequiredImport(type)) {
                    imports.add(type);
                  }
                }
                if (e.getReturnType() != null) {
                  String type = objcTypeForType(e.getReturnType());
                  if (isRequiredImport(type)) {
                    imports.add(type);
                  }
                }
              }
              List<String> sortedImports = new ArrayList<String>(imports);
              Collections.sort(sortedImports);
              return sortedImports;
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
              return doxygenDocString(end.getDocString());
            }
            else if ("methodName".equals(propertyName)) {
              StringBuilder sb = new StringBuilder();

              sb.append("- (" + getBuilderReturnType(end) + " *)");

              sb.append(getMethodizedPath(end));

              if (end.getEntity() != null) {
                if (end.getPathParameters() != null && end.getPathParameters().size() > 0) {
                  sb.append(" entity");
                }
                String type = objcTypeForType(end.getEntity());
                sb.append(":(" + type + " *)");
                String paramName = type;
                if (type.startsWith(classPrefix)) {
                  paramName = downcase(type.substring(classPrefix.length()));
                }
                sb.append(paramName);
              }

              return sb.toString();
            }

            else if ("requestBuilderClassName".equals(propertyName)) {
              return getBuilderReturnType(end);
            }

            else if ("requestBuilderBlockName".equals(propertyName)) {
              return getBuilderReturnType(end) + "Block";
            }

            else if ("entityReturnType".equals(propertyName)) {
              String type = objcFlatpackReturnType(end.getReturnType());
              return type.equals("void") ? null : type;
            }

            else if ("entityReturnName".equals(propertyName)) {
              if (end.getReturnType() == null) return null;

              String name = "result";
              if (end.getReturnType().getName() != null) {
                name = end.getReturnType().getName();
              }

              if (end.getReturnType().getListElement() != null) {
                if (end.getReturnType().getListElement().getName() != null) {
                  name = end.getReturnType().getListElement().getName();
                }
                name = pluralOf(name);
              }

              return name;
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

    group.registerModelAdaptor(EntityDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {

            EntityDescription entity = (EntityDescription) o;
            if ("importNames".equals(propertyName)) {
              Set<String> imports = new HashSet<String>();
              String type = requireNameForType(entity.getTypeName());
              if (isRequiredImport(type)) {
                imports.add(type);
              }
              for (Property p : entity.getProperties()) {
                String name = null;
                if (p.getType().getListElement() != null) {
                  name = objcTypeForType(p.getType().getListElement());
                }
                else {
                  name = objcTypeForProperty(p);
                }
                if (name != null && isRequiredImport(name)) {
                  imports.add(name);
                }
              }
              List<String> sortedImports = new ArrayList<String>(imports);
              Collections.sort(sortedImports);
              return sortedImports;
            }

            if ("docString".equals(propertyName)) {
              return doxygenDocString(entity.getDocString());
            }

            else if ("typeNameCapitalized".equals(propertyName)) {
              return upcase(entity.getTypeName());
            }

            else if ("payloadName".equals(propertyName)) {
              return entity.getTypeName();
            }

            else if ("supertype".equals(propertyName)) {
              EntityDescription supertype = entity.getSupertype();
              return supertype == null ? new EntityDescription("baseHasUuid", null) : supertype;
            }

            else if ("requireName".equals(propertyName)) {
              return requireNameForType(entity.getTypeName());
            }

            else if ("properties".equals(propertyName)) {
              return getSortedProperties(entity);
            }

            else if ("attributes".equals(propertyName)) {
              return filterProperties(entity, false);
            }

            else if ("relationships".equals(propertyName)) {
              return filterProperties(entity, true);
            }

            else if ("entityProperties".equals(propertyName)) {

              Map<String, Property> propertyMap = new HashMap<String, Property>();
              for (Property p : entity.getProperties()) {

                // TODO if we decide to encode enum types, we'll want to remove the second condition
                if (p.getType().getName() != null) {
                  propertyMap.put(p.getName(), p);
                }
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
              List<Property> properties = new ArrayList<Property>();
              for (Property p : entity.getProperties()) {
                if (p.getType().getJsonKind().equals(JsonKind.LIST)) {
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
          String docString = p.getDocString();

          List<String> enumValues = p.getType().getEnumValues();
          if (enumValues != null) {
            docString = docString == null ? "" : docString;
            docString += "\n\nPossible values: ";
            for (int i = 0; i < enumValues.size(); i++) {
              docString += enumValues.get(i);
              if (i < enumValues.size() - 1) docString += ", ";
            }
          }
          return doxygenDocString(docString);
        }
        else if ("requireName".equals(propertyName)) {
          return requireNameForType(p.getType().getName());
        }
        else if ("nameCapitalized".equals(propertyName)) {
          return upcase(p.getName());
        }
        else if ("objcType".equals(propertyName)) {
          return objcTypeForProperty(p);
        }
        else if ("modifiers".equals(propertyName)) {
          List<String> modifiers = new ArrayList<String>();
          String safeName = getSafeName(p.getName());
          if (p.getImpliedProperty() != null
            && p.getImpliedProperty().getType().getJsonKind().equals(JsonKind.LIST)) {
            modifiers.add("weak");
          }
          else {
            modifiers.add("strong");
          }
          if (p.getType().getJsonKind().equals(JsonKind.BOOLEAN)) {
            modifiers.add("getter=is" + upcase(safeName));
          }
          // http://developer.apple.com/library/ios/#documentation/Cocoa/Conceptual/MemoryMgmt/Articles/mmRules.html
          if (p.getName().startsWith("new")) {
            modifiers.add("getter=a" + upcase(safeName));
          }
          return modifiers;
        }
        else if ("safeName".equals(propertyName)) {
          return getSafeName(p.getName());
        }
        else if ("upcaseName".equals(propertyName)) {
          return upcase(getSafeName(p.getName()));
        }
        else if ("listElementObjcType".equals(propertyName)) {
          return objcTypeForType(p.getType().getListElement());
        }
        else if ("singularUpcaseName".equals(propertyName)) {
          if (p.getType().getListElement() != null) {
            String type = objcTypeForType(p.getType().getListElement());
            return type.substring(2);
          }
          return upcase(p.getName());
        }

        return super.getProperty(interp, self, o, property, propertyName);
      }
    });

    group.registerModelAdaptor(Type.class, new ObjectModelAdaptor() {

      @Override
      public Object getProperty(Interpreter interp, ST self, Object o,
          Object property, String propertyName)
          throws STNoSuchPropertyException {

        if (propertyName.equals("name")) {
          return ((Type) o).getName();
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

    Map<String, Object> namesMap = new HashMap<String, Object>();
    namesMap.put("classPrefix", classPrefix);
    group.defineDictionary("names", namesMap);

    return group;
  }

  private String objcFlatpackReturnType(Type type) {
    String returnType = "void";
    if (type != null) {
      if (type.getListElement() != null) {
        returnType = "NSArray *";
      }
      else if (type.getMapKey() != null) {
        returnType = "NSDictionary *";
      }
      else if (type.getName() != null) {
        returnType = objcTypeForType(type) + " *";
      }
    }
    return returnType;
  }

  private String objcTypeForProperty(Property p) {
    if (p.isEmbedded()) {
      return classPrefix + upcase(p.getType().getName());
    }

    return objcTypeForType(p.getType());
  }

  private String objcTypeForType(Type type) {

    if (type.getName() != null) {
      // if the type is an enum, the type will be a string
      if (type.getEnumValues() != null) {
        return "NSString";
      }

      String name = type.getName();
      String prefix = classPrefix;
      if (name.equalsIgnoreCase("baseHasUuid")) {
        prefix = "FP";
      }
      return prefix + upcase(type.getName());
    }

    String objcType = "nil";
    switch (type.getJsonKind()) {
      case BOOLEAN:
        objcType = "NSNumber";
        break;
      case DOUBLE:
        objcType = "NSNumber";
        break;
      case ANY:
        objcType = "NSObject";
        break;
      case INTEGER:
        objcType = "NSNumber";
        break;
      case LIST:
        objcType = "NSMutableArray";
        break;
      case MAP:
        objcType = "NSMutableDictionary";
        break;
      case NULL:
        objcType = "nil";
        break;
      case STRING:
        objcType = "NSString";
        break;
      default:
        break;
    }

    return objcType;

  }

  private void render(ST enumST, File packageDir, String fileName)
      throws IOException {

    if (!packageDir.isDirectory() && !packageDir.mkdirs()) {
      logger
          .error("Could not create output directory {}", packageDir.getPath());
      return;
    }
    Writer fileWriter = new OutputStreamWriter(new FileOutputStream(new File(
        packageDir, classPrefix + fileName)), "UTF8");
    AutoIndentWriter writer = new AutoIndentWriter(fileWriter);
    writer.setLineWidth(80);
    enumST.write(writer);
    fileWriter.close();
  }

  private String requireNameForType(String type) {
    return type.equalsIgnoreCase("baseHasUuid") ? "FP" + upcase(type) : classPrefix + upcase(type);
  }
}
