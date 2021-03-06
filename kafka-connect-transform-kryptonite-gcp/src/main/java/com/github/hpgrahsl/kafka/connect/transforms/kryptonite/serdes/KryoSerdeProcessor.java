/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.connect.data.ConnectSchema;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Schema.Type;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KryoSerdeProcessor implements SerdeProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(KryoSerdeProcessor.class);
  private final int outputBufSize;
  private final int outputBufMaxSize;

  public KryoSerdeProcessor(int outputBufSize, int outputBufMaxSize) {
    this.outputBufSize = outputBufSize;
    this.outputBufMaxSize = outputBufMaxSize;
  }

  public byte[] objectToBytes(Object object, Class<?> clazz) {
    return objectToBytes(object);
  }

  public byte[] objectToBytes(Object object) {
    Output output = new Output(outputBufSize, outputBufMaxSize);
    KryoInstance.get().writeClassAndObject(output, object);
    return output.toBytes();
  }

  public Object bytesToObject(byte[] bytes, Class<?> clazz) {
    return bytesToObject(bytes);
  }

  public Object bytesToObject(byte[] bytes) {
    Input input = new Input(bytes);
    return KryoInstance.get().readClassAndObject(input);
  }

  public static class StructSerializer extends Serializer<Struct> {

    private final SchemaSerializer schemaSerializer = new SchemaSerializer();

    public void write(Kryo kryo, Output output, Struct struct) {
      LOGGER.info("writing struct's schema");
      kryo.writeObject(output, struct.schema(), schemaSerializer);
      writeStructFieldObjects(kryo, output, struct);
    }

    private void writeStructFieldObjects(Kryo kryo, Output output, Struct struct) {
      LOGGER.info("writing struct objects one by one...");
      struct
          .schema()
          .fields()
          .forEach(
              f -> {
                LOGGER.info("write full field '{}' of type {}", f.name(), f.schema().type());
                if (f.schema().type() != Type.STRUCT) {
                  kryo.writeClassAndObject(output, struct.get(f));
                } else {
                  writeStructFieldObjects(kryo, output, (Struct) struct.get(f));
                }
              });
    }

    public Struct read(Kryo kryo, Input input, Class<? extends Struct> type) {
      LOGGER.info("reading struct's schema");
      Schema schema = kryo.readObject(input, Schema.class, schemaSerializer);
      return readStructFieldObjects(kryo, input, new Struct(schema));
    }

    private Struct readStructFieldObjects(Kryo kryo, Input input, Struct struct) {
      LOGGER.info("reading struct objects one by one...");
      struct
          .schema()
          .fields()
          .forEach(
              f -> {
                LOGGER.info("read full field '{}' of type {}", f.name(), f.schema().type());
                if (f.schema().type() != Type.STRUCT) {
                  struct.put(f, kryo.readClassAndObject(input));
                } else {
                  struct.put(f, readStructFieldObjects(kryo, input, new Struct(f.schema())));
                }
              });
      return struct;
    }
  }

  public static class SchemaSerializer extends Serializer<Schema> {

    public void write(Kryo kryo, Output output, Schema object) {
      LOGGER.trace("writing basic schema info for type {}", object.type());
      kryo.writeClassAndObject(output, object.type());
      output.writeString(object.name());
      output.writeBoolean(object.isOptional());
      Object defaultValue = object.defaultValue();
      kryo.writeObjectOrNull(
          output, defaultValue, defaultValue != null ? defaultValue.getClass() : Object.class);
      kryo.writeObjectOrNull(output, object.version(), Integer.class);
      output.writeString(object.doc());

      if (Type.STRUCT == object.type()) {
        LOGGER.trace("writing struct type schema info");
        output.writeInt(object.fields().size());
        object
            .fields()
            .forEach(
                f -> {
                  LOGGER.trace(
                      "writing field name '{}' with index '{}' and schema '{}'",
                      f.name(),
                      f.index(),
                      f.schema().type());
                  output.writeString(f.name());
                  output.writeInt(f.index());
                  write(kryo, output, f.schema());
                });
      } else if (Type.ARRAY == object.type()) {
        LOGGER.trace("writing array type schema info");
        write(kryo, output, object.valueSchema());
      } else if (Type.MAP == object.type()) {
        LOGGER.trace("writing map type schema info");
        write(kryo, output, object.keySchema());
        write(kryo, output, object.valueSchema());
      }
    }

    public Schema read(Kryo kryo, Input input, Class<? extends Schema> type) {
      Type schemaType = (Type) kryo.readClassAndObject(input);
      LOGGER.trace("reading basic schema info for type {}", schemaType);
      String name = input.readString();
      Boolean isOptional = input.readBoolean();
      Object defaultValue = kryo.readObjectOrNull(input, Object.class);
      Integer version = kryo.readObjectOrNull(input, Integer.class);
      String doc = input.readString();

      if (Type.STRUCT == schemaType) {
        LOGGER.trace("reading struct type schema info");
        int numFields = input.readInt();
        List<Field> fields = new ArrayList<>();
        while (--numFields >= 0) {
          String fName = input.readString();
          int fIndex = input.readInt();
          Schema fSchema = read(kryo, input, Schema.class);
          LOGGER.trace(
              "adding field name '{}' with index '{}' and schema '{}'",
              fName,
              fIndex,
              fSchema.type());
          fields.add(new Field(fName, fIndex, fSchema));
        }
        LOGGER.trace("returning struct schema");
        return new ConnectSchema(
            schemaType, isOptional, defaultValue, name, version, doc, null, fields, null, null);
      } else if (Type.ARRAY == schemaType) {
        LOGGER.trace("reading array type schema info");
        Schema vSchema = read(kryo, input, Schema.class);
        LOGGER.trace("returning array schema");
        return new ConnectSchema(
            schemaType, isOptional, defaultValue, name, version, doc, null, null, null, vSchema);
      } else if (Type.MAP == schemaType) {
        LOGGER.trace("reading map type schema info");
        Schema kSchema = read(kryo, input, Schema.class);
        Schema vSchema = read(kryo, input, Schema.class);
        LOGGER.trace("returning map schema");
        return new ConnectSchema(
            schemaType, isOptional, defaultValue, name, version, doc, null, null, kSchema, vSchema);
      } else {
        LOGGER.trace("returning {} schema", schemaType);
        return new ConnectSchema(schemaType, isOptional, defaultValue, name, version, doc);
      }
    }
  }
}
