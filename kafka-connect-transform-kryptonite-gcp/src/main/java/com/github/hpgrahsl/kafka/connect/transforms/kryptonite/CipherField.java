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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.CipherDataKeysValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.CipherEncodingValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.CipherModeValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.CipherNameValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.FieldConfigValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.FieldModeValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.KeySourceValidator;
import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators.TimeUnitValidator;
import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.ConfigDataKeyVault;
import com.github.hpgrahsl.kryptonite.GcpSecretManagerKeyVault;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.NoOpKeyStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.common.cache.Cache;
import org.apache.kafka.common.cache.LRUCache;
import org.apache.kafka.common.cache.SynchronizedCache;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.NonEmptyString;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CipherField<R extends ConnectRecord<R>> implements Transformation<R> {

  public enum FieldMode {
    ELEMENT,
    OBJECT
  }

  public enum KeySource {
    CONFIG,
    GCP_SECRET_MANAGER,
    GCP_SECRET_MANAGER_WITH_KMS
  }

  public static final String OVERVIEW_DOC =
      "Encrypt/Decrypt specified record fields with AEAD cipher."
          + "<p/>The transformation should currently only be used for the record value (<code>"
          + CipherField.Value.class.getName()
          + "</code>)."
          + "Future versions will support a dedicated 'mode of operation' applicable also to the record key (<code>"
          + CipherField.Key.class.getName()
          + "</code>) or value .";

  public static final String FIELD_CONFIG = "field_config";
  public static final String PATH_DELIMITER = "path_delimiter";
  public static final String FIELD_MODE = "field_mode";
  public static final String CIPHER_ALGORITHM = "cipher_algorithm";
  public static final String CIPHER_DATA_KEY_NAME = "cipher_data_key_name";
  public static final String CIPHER_DATA_KEY_VERSION = "cipher_data_key_version";
  public static final String CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION =
      "cipher_data_key_cache_expiry_duration";
  public static final String CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_UNIT =
      "cipher_data_key_cache_expiry_duration_unit";
  public static final String CIPHER_DATA_KEYS = "cipher_data_keys";
  public static final String CIPHER_TEXT_ENCODING = "cipher_text_encoding";
  public static final String CIPHER_MODE = "cipher_mode";
  public static final String KEY_SOURCE = "key_source";
  public static final String KMS_KEY_NAME = "kms_key_name";

  private static final String PATH_DELIMITER_DEFAULT = ".";
  private static final String FIELD_MODE_DEFAULT = "ELEMENT";
  private static final String CIPHER_ALGORITHM_DEFAULT = "AES/GCM/NoPadding";
  private static final long CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_DEFAULT = 24L;
  private static final String CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_UNIT_DEFAULT = "HOURS";
  private static final String CIPHER_TEXT_ENCODING_DEFAULT = "base64";
  private static final String KEY_SOURCE_DEFAULT = "CONFIG";
  private static final String CIPHER_DATA_KEYS_DEFAULT = "[]";
  private static final String KMS_KEY_NAME_DEFAULT = null;

  public static final ConfigDef CONFIG_DEF =
      new ConfigDef()
          .define(
              FIELD_CONFIG,
              Type.STRING,
              ConfigDef.NO_DEFAULT_VALUE,
              new FieldConfigValidator(),
              Importance.HIGH,
              "JSON array with field config objects specifying which fields together with their settings "
                  + "should get either encrypted / decrypted (nested field names are expected to be separated by '.' "
                  + "per default, or by a custom 'path_delimiter' config")
          .define(
              PATH_DELIMITER,
              Type.STRING,
              PATH_DELIMITER_DEFAULT,
              new NonEmptyString(),
              Importance.LOW,
              "path delimiter used as field name separator when referring to nested fields "
                  + "in the input record")
          .define(
              FIELD_MODE,
              Type.STRING,
              FIELD_MODE_DEFAULT,
              new FieldModeValidator(),
              Importance.MEDIUM,
              "defines how to process complex field types (maps, lists, structs), either as full objects "
                  + "or element-wise")
          .define(
              CIPHER_ALGORITHM,
              Type.STRING,
              CIPHER_ALGORITHM_DEFAULT,
              new CipherNameValidator(),
              Importance.LOW,
              "cipher algorithm used for data encryption (currently supports only one AEAD cipher: "
                  + CIPHER_ALGORITHM_DEFAULT
                  + ")")
          .define(
              CIPHER_DATA_KEYS,
              Type.PASSWORD,
              CIPHER_DATA_KEYS_DEFAULT,
              new CipherDataKeysValidator(),
              Importance.MEDIUM,
              "JSON array with data key objects specifying the key name, key version and base64 encoded "
                  + "key bytes used for encryption / decryption")
          .define(
              CIPHER_DATA_KEY_NAME,
              Type.STRING,
              ConfigDef.NO_DEFAULT_VALUE,
              new NonEmptyString(),
              Importance.HIGH,
              "secret key name to be used as default data encryption key for all fields which don't refer to "
                  + "a field-specific secret key name")
          .define(
              CIPHER_DATA_KEY_VERSION,
              Type.STRING,
              ConfigDef.NO_DEFAULT_VALUE,
              new NonEmptyString(),
              Importance.HIGH,
              "secret key version to be used as default data encryption key for all fields which don't refer to "
                  + "a field-specific secret key version")
          .define(
              CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION,
              Type.LONG,
              CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_DEFAULT,
              Importance.LOW,
              "defines the expiration duration of the secret key cache")
          .define(
              CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_UNIT,
              Type.STRING,
              CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_UNIT_DEFAULT,
              new TimeUnitValidator(),
              Importance.LOW,
              "defines the unit of expiration duration of the private key cache")
          .define(
              CIPHER_TEXT_ENCODING,
              Type.STRING,
              CIPHER_TEXT_ENCODING_DEFAULT,
              new CipherEncodingValidator(),
              ConfigDef.Importance.LOW,
              "defines the encoding of the resulting ciphertext bytes (currently only supports 'base64')")
          .define(
              CIPHER_MODE,
              Type.STRING,
              ConfigDef.NO_DEFAULT_VALUE,
              new CipherModeValidator(),
              ConfigDef.Importance.HIGH,
              "defines whether the data should get encrypted or decrypted")
          .define(
              KEY_SOURCE,
              Type.STRING,
              KEY_SOURCE_DEFAULT,
              new KeySourceValidator(),
              ConfigDef.Importance.HIGH,
              "defines the origin of the secret key material (currently supports keys specified in the config or "
                  + "gcp secret manager)")
          .define(
              KMS_KEY_NAME,
              Type.STRING,
              KMS_KEY_NAME_DEFAULT,
              Importance.MEDIUM,
              "The GCP Cloud KMS key name for decrypting a data encryption key (DEK), "
                  + "if the DEK is encrypted with a key encryption key (KEK)");

  private static final String PURPOSE = "(de)cipher record fields";

  private static final Logger LOGGER = LoggerFactory.getLogger(CipherField.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private RecordHandler recordHandlerWithSchema;
  private RecordHandler recordHandlerWithoutSchema;
  private SchemaRewriter schemaRewriter;
  private Cache<Schema, Schema> schemaCache;

  @Override
  public R apply(R record) {
    if (operatingSchema(record) == null) {
      return processWithoutSchema(record);
    } else {
      return processWithSchema(record);
    }
  }

  public R processWithoutSchema(R record) {
    LOGGER.debug("processing schemaless data");
    Map<String, Object> valueMap = requireMap(operatingValue(record), PURPOSE);
    Map<String, Object> updatedValueMap = new LinkedHashMap<>(valueMap);
    recordHandlerWithoutSchema.matchFields(null, valueMap, null, updatedValueMap, "");
    return newRecord(record, null, updatedValueMap);
  }

  public R processWithSchema(R record) {
    LOGGER.debug("processing schema-aware data");
    Struct valueStruct = requireStruct(operatingValue(record), PURPOSE);
    Schema updatedSchema = schemaCache.get(valueStruct.schema());
    if (updatedSchema == null) {
      LOGGER.debug("adapting schema because record's schema not present in cache");
      updatedSchema = schemaRewriter.adaptSchema(valueStruct.schema(), "");
      schemaCache.put(valueStruct.schema(), updatedSchema);
    }
    Struct updatedValueStruct = new Struct(updatedSchema);
    recordHandlerWithSchema.matchFields(
        valueStruct.schema(), valueStruct, updatedSchema, updatedValueStruct, "");
    return newRecord(record, updatedSchema, updatedValueStruct);
  }

  @Override
  public ConfigDef config() {
    return CONFIG_DEF;
  }

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> props) {
    try {
      SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);
      Map<String, FieldConfig> fieldPathMap =
          OBJECT_MAPPER
              .readValue(config.getString(FIELD_CONFIG), new TypeReference<Set<FieldConfig>>() {})
              .stream()
              .collect(Collectors.toMap(FieldConfig::getName, Function.identity()));
      Kryptonite kryptonite = configureKryptonite(config);
      SerdeProcessor serdeProcessor = new KryoSerdeProcessor();
      recordHandlerWithSchema =
          new SchemaawareRecordHandler(
              config,
              serdeProcessor,
              kryptonite,
              CipherMode.valueOf(config.getString(CIPHER_MODE)),
              fieldPathMap);
      recordHandlerWithoutSchema =
          new SchemalessRecordHandler(
              config,
              serdeProcessor,
              kryptonite,
              CipherMode.valueOf(config.getString(CIPHER_MODE)),
              fieldPathMap);
      schemaRewriter =
          new SchemaRewriter(
              fieldPathMap,
              FieldMode.valueOf(config.getString(FIELD_MODE)),
              CipherMode.valueOf(config.getString(CIPHER_MODE)),
              config.getString(PATH_DELIMITER));
      schemaCache = new SynchronizedCache<>(new LRUCache<>(16));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      throw new ConfigException(e.getMessage());
    }
  }

  private Kryptonite configureKryptonite(SimpleConfig config) {
    try {
      KeySource keySource = KeySource.valueOf(config.getString(KEY_SOURCE));
      switch (keySource) {
        case CONFIG:
          Set<DataKeyConfig> dataKeyConfig =
              OBJECT_MAPPER.readValue(
                  config.getPassword(CIPHER_DATA_KEYS).value(),
                  new TypeReference<Set<DataKeyConfig>>() {});
          Map<String, byte[]> configKeyMap =
              dataKeyConfig.stream()
                  .collect(
                      Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getKeyBytes));
          return new Kryptonite(new ConfigDataKeyVault(configKeyMap));
        case GCP_SECRET_MANAGER:
          return new Kryptonite(
              new GcpSecretManagerKeyVault(
                  config.getString(CIPHER_DATA_KEY_NAME),
                  new NoOpKeyStrategy(),
                  config.getLong(CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION),
                  config.getString(CIPHER_DATA_KEY_CACHE_EXPIRY_DURATION_UNIT)));
        case GCP_SECRET_MANAGER_WITH_KMS:
          return new Kryptonite(
              new GcpSecretManagerKeyVault(
                  config.getString(CIPHER_DATA_KEY_NAME), config.getString(KMS_KEY_NAME)));
        default:
          throw new ConfigException(
              "failed to configure kryptonite instance due to invalid key source");
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      throw new ConfigException(e.getMessage(), e);
    }
  }

  protected abstract Schema operatingSchema(R record);

  protected abstract Object operatingValue(R record);

  protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

  public static final class Key<R extends ConnectRecord<R>> extends CipherField<R> {

    @Override
    protected Schema operatingSchema(R record) {
      return record.keySchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.key();
    }

    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(
          record.topic(),
          record.kafkaPartition(),
          updatedSchema,
          updatedValue,
          record.valueSchema(),
          record.value(),
          record.timestamp());
    }
  }

  public static final class Value<R extends ConnectRecord<R>> extends CipherField<R> {

    @Override
    protected Schema operatingSchema(R record) {
      return record.valueSchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.value();
    }

    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(
          record.topic(),
          record.kafkaPartition(),
          record.keySchema(),
          record.key(),
          updatedSchema,
          updatedValue,
          record.timestamp());
    }
  }
}
