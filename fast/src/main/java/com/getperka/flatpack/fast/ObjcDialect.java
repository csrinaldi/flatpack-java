package com.getperka.flatpack.fast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.ArrayList;
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

import com.getperka.flatpack.BaseHasUuid;
import com.getperka.flatpack.client.dto.ApiDescription;
import com.getperka.flatpack.client.dto.EndpointDescription;
import com.getperka.flatpack.client.dto.EntityDescription;
import com.getperka.flatpack.client.dto.ParameterDescription;
import com.getperka.flatpack.ext.Property;
import com.getperka.flatpack.ext.Type;
import com.getperka.flatpack.util.FlatPackCollections;

public class ObjcDialect implements Dialect {

  private static final Logger logger = LoggerFactory.getLogger(ObjcDialect.class);

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
    File modelOutput = new File(outputDir, "");
    for (EntityDescription entity : allEntities.values()) {
      entityST = group.getInstanceOf("entityHeader").add("entity", entity);
      render(entityST, modelOutput, upcase(entity.getTypeName()) + ".h");

      entityST = group.getInstanceOf("entity").add("entity", entity);
      render(entityST, modelOutput, upcase(entity.getTypeName()) + ".m");
    }
  }

  @Override
  public String getDialectName() {
    return "objc";
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

    group.registerModelAdaptor(EndpointDescription.class,
        new ObjectModelAdaptor() {
          @Override
          public Object getProperty(Interpreter interp, ST self, Object o,
              Object property, String propertyName)
              throws STNoSuchPropertyException {
            EndpointDescription end = (EndpointDescription) o;
            if ("className".equals(propertyName) || "methodName".equals(propertyName)) {
              // Convert a path like /api/2/foo/bar/{}/baz to FooBarBazMethod
              String path = end.getPath();
              String[] parts = path.split(Pattern.quote("/"));
              StringBuilder sb = new StringBuilder();
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
              sb.append(upcase(end.getMethod().toLowerCase()));
              String name = sb.toString();

              return "className".equals(propertyName) ? upcase(name) : name;
            } else if ("pathDecoded".equals(propertyName)) {
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
            if ("payloadName".equals(propertyName)) {
              return entity.getTypeName();
            }

            else if ("supertype".equals(propertyName)) {
              EntityDescription supertype = entity.getSupertype();
              return supertype == null ? new EntityDescription("baseHasUuid", null) : supertype;
            }

            else if ("simpleName".equals(propertyName)) {
              if (entity.getTypeName().equals("baseHasUuid")) {
                return "Flatpack::Core::" + BaseHasUuid.class.getSimpleName();
              }
              return upcase(entity.getTypeName());
            }

            else if ("requireName".equals(propertyName)) {
              return requireNameForType(entity.getTypeName());
            }

            else if ("properties".equals(propertyName)) {
              List<Property> properties = new ArrayList<Property>();
              for (Property p : entity.getProperties()) {
                if (!p.isEmbedded()) {
                  properties.add(p);
                }
              }
              return properties;
            }

            else if ("entityProperties".equals(propertyName)) {

              Map<String, Property> propertyMap = new HashMap<String, Property>();
              for (Property p : entity.getProperties()) {

                // TODO if we decide to encode enum types, we'll want to remove the second condition
                if (p.getType().getName() != null && p.getType().getEnumValues() == null) {
                  propertyMap.put(p.getName(), p);
                }
              }

              return propertyMap.values();
            }

            else if ("embeddedEntityProperties".equals(propertyName)) {
              Map<String, Property> propertyMap = new HashMap<String, Property>();
              for (Property p : entity.getProperties()) {
                if (p.getType().getName() != null &&
                  p.getType().getEnumValues() == null && p.isEmbedded()) {

                  propertyMap.put(p.getName(), p);
                }
              }
              return propertyMap.values();
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
            if ("underscoreName".equals(propertyName)) {
              return param.getName();
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
        if ("attrName".equals(propertyName)) {
          return p.getName();
        }

        else if ("requireName".equals(propertyName)) {
          return requireNameForType(p.getType().getName());
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
    // namesMap.put("gemName", gemName);
    // namesMap.put("moduleName", moduleName);
    // namesMap.put("modelModuleName", modelModuleName);
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

  private String requireNameForType(String type) {
    return type;
  }
}
