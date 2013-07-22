package com.getperka.flatpack.fast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
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

public class JavaScriptDialect implements Dialect {

  @Flag(tag = "packageName",
      help = "The name of the package that generated sources should belong to",
      defaultValue = "com.getperka.client")
  static String packageName;

  private static final Logger logger = LoggerFactory.getLogger(JavaScriptDialect.class);

  private static String upcase(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private List<String> entityRequires;

  @Override
  public void generate(ApiDescription api, File outputDir) throws IOException {

    if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
      logger.error("Could not create output directory {}", outputDir.getPath());
      return;
    }

    // first collect just our model entities
    Set<String> requires = new HashSet<String>();
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
      requires.add(packageName + "." + upcase(entity.getTypeName()));
    }
    // Ensure that the "real" implementations are used
    allEntities.remove("baseHasUuid");
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

    return upcase(sb.toString());
  }

  private String getNameForType(Type type) {
    String name = type.getName();
    name = name == null || name.trim().length() == 0 ? type.getJsonKind().name()
        .toLowerCase() : name;
    return name;
  }

  private boolean isRequiredImport(String type) {
    return type != null
      && !type.equalsIgnoreCase("object")
      && !type.equalsIgnoreCase("number")
      && !type.equalsIgnoreCase("string")
      && !type.equalsIgnoreCase("boolean")
      && !type.equalsIgnoreCase("null")
      && !type.equalsIgnoreCase("undefined")
      && !type.startsWith("Backbone");
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
        if (type.getListElement() != null && type.getListElement().getEnumValues() != null) {
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
              String prefix = packageName;
              String typeName = entity.getTypeName();
              if (entity.getTypeName().equalsIgnoreCase("baseHasUuid")) {
                prefix = "com.getperka.flatpack.core";
              }

              return prefix + "." + upcase(typeName);
            }

            else if ("supertype".equals(propertyName)) {
              EntityDescription supertype = entity.getSupertype();
              return supertype == null ? new EntityDescription("baseHasUuid", null) : supertype;
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
              List<Property> properties = new ArrayList<Property>();
              for (Property p : entity.getProperties()) {
                if (p.getType().getListElement() != null &&
                  !jsTypeForType(p.getType().getListElement()).equals("String")) {
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
          if (p.getType().getListElement() != null) {
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
              return (collectionModelType);
            }
          }
        }

        else if ("defaultValue".equals(propertyName)) {
          String defaultVal = "undefined";
          if (p.getType().getJsonKind().equals(JsonKind.LIST) &&
            jsTypeForType(p.getType().getListElement()).equals("String")) {
            defaultVal = "[]";
            jsTypeForType(p.getType());
          }
          else if (p.getType().getJsonKind().equals(JsonKind.LIST) &&
            !jsTypeForType(p.getType().getListElement()).equals("String")) {
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
                EndpointDescription ed = iter.next();
                if (ed.getPath() != null && ed.getPath().contains("{path:.*}")) {
                  iter.remove();
                }
              }
              return sortedEndpoints;
            }
            else if ("endpointsWithQueryParams".equals(propertyName)) {
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
                EndpointDescription desc = iter.next();
                if (desc.getQueryParameters() == null || desc.getQueryParameters().isEmpty()) {
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
              if (end.getQueryParameters() != null && !end.getQueryParameters().isEmpty()) {
                return getBuilderReturnType(end);
              }
              else if (end.getReturnType() != null && end.getReturnType().getUuid() != null) {
                return "com.getperka.flatpack.client.FlatpackRequest";
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
