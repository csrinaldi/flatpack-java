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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.util.FlatPackCollections;

public class JavaScriptDialect implements Dialect {

  @Flag(tag = "packageName",
      help = "The name of the package that generated sources should belong to",
      defaultValue = "com.getperka.client")
  static String packageName;

  static Map<String, String> packageMap = new HashMap<String, String>() {
    {
      // put("baseApi", "com.getperka.flatpack.client");
      // put("baseRequest", "com.getperka.flatpack.client");
      // put("flatpackRequest", "com.getperka.flatpack.client");
      // put("jsonRequest", "com.getperka.flatpack.client");
      put("baseHasUuid", "com.getperka.flatpack.core");
      // put("entityDescription", "com.getperka.flatpack.core");
      // put("flatpack", "com.getperka.flatpack.core");
      // put("packer", "com.getperka.flatpack.core");
      // put("property", "com.getperka.flatpack.core");
      // put("unpacker", "com.getperka.flatpack.core");
    }
  };

  private static final Logger logger = LoggerFactory.getLogger(JavaScriptDialect.class);

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
      entityST = group.getInstanceOf("entity").add("entity", entity);
      render(entityST, outputDir, camelCaseToUnderscore(entity.getTypeName()) + ".js");
    }

    // render api stubs
    ST apiST = group.getInstanceOf("api").add("api", api);
    render(apiST, outputDir, "base_api.js");
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

  private String jsTypeForType(Type type) {
    if (type.getName() != null) {
      // if the type is an enum, the type will be a string
      if (type.getEnumValues() != null) {
        return "String";
      }
      return packageName + "." + upcase(type.getName());
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
        jsType = "Backbone.Collection";
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

            if ("canonicalName".equals(propertyName)) {
              String prefix = packageName;
              String typeName = entity.getTypeName();
              if (packageMap.containsKey(typeName)) {
                prefix = packageMap.get(typeName);
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
        }

        if ("jsType".equals(propertyName)) {
          return jsTypeForType(p.getType());
        }

        if ("defaultValue".equals(propertyName)) {
          return p.getType().getJsonKind().equals(JsonKind.LIST) ?
              "new Backbone.Collection()" : "undefined";
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
              return sortedEndpoints;
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
            if ("methodName".equals(propertyName)) {
              StringBuilder sb = new StringBuilder();

              sb.append("- (" + getBuilderReturnType(end) + " *)");

              if (end.getEntity() != null) {
                if (end.getPathParameters() != null && end.getPathParameters().size() > 0) {
                  sb.append(" entity");
                }
                String type = jsTypeForType(end.getEntity());
                sb.append(":(" + type + " *)");
                String paramName = type;
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