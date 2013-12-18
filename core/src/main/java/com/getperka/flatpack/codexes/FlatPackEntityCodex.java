package com.getperka.flatpack.codexes;

import javax.inject.Inject;

import com.getperka.flatpack.FlatPackEntity;
import com.getperka.flatpack.Packer;
import com.getperka.flatpack.Unpacker;
import com.getperka.flatpack.ext.DeserializationContext;
import com.getperka.flatpack.ext.JsonKind;
import com.getperka.flatpack.ext.SerializationContext;
import com.getperka.flatpack.ext.Type;
import com.google.gson.JsonElement;
import com.google.gson.internal.Streams;
import com.google.inject.TypeLiteral;

/**
 * Allows FlatPack payloads to be nested within other payloads.
 * 
 * @param <T>
 */
public class FlatPackEntityCodex<T> extends ValueCodex<FlatPackEntity<T>> {
  @Inject
  private Packer packer;
  @Inject
  private Unpacker unpacker;
  @Inject
  private TypeLiteral<T> valueType;

  private static final Type TYPE = new Type.Builder().withJsonKind(JsonKind.ANY).build();

  @Override
  public Type describe() {
    return TYPE;
  }

  @Override
  public FlatPackEntity<T> readNotNull(JsonElement element, DeserializationContext context)
      throws Exception {
    return unpacker.unpack(valueType.getType(), element, context.getPrincipal());
  }

  @Override
  public void writeNotNull(FlatPackEntity<T> object, SerializationContext context) throws Exception {
    JsonElement elt = packer.pack(object);
    Streams.write(elt, context.getWriter());
  }

}
