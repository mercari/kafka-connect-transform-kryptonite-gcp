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

import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes.SerdeProcessor;
import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.errors.DataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RecordHandler implements FieldPathMatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordHandler.class);

  private final AbstractConfig config;
  private final SerdeProcessor serdeProcessor;
  private final Kryptonite kryptonite;

  protected final String pathDelimiter;
  protected final CipherMode cipherMode;
  protected final Map<String, FieldConfig> fieldConfig;

  public RecordHandler(
      AbstractConfig config,
      SerdeProcessor serdeProcessor,
      Kryptonite kryptonite,
      CipherMode cipherMode,
      Map<String, FieldConfig> fieldConfig) {
    this.config = config;
    this.serdeProcessor = serdeProcessor;
    this.kryptonite = kryptonite;
    this.pathDelimiter = config.getString(CipherField.PATH_DELIMITER);
    this.cipherMode = cipherMode;
    this.fieldConfig = fieldConfig;
  }

  public AbstractConfig getConfig() {
    return config;
  }

  public Kryptonite getKryptonite() {
    return kryptonite;
  }

  public Object processField(Object object, String matchedPath) {
    try {
      LOGGER.debug("{} field {}", cipherMode, matchedPath);
      FieldMetaData fieldMetaData = determineFieldMetaData(object, matchedPath);
      LOGGER.trace("field meta-data for path '{}' {}", matchedPath, fieldMetaData);
      if (CipherMode.ENCRYPT == cipherMode) {
        byte[] valueBytes = serdeProcessor.objectToBytes(object);
        String cipherText = kryptonite.cipher(valueBytes, fieldMetaData);
        return cipherText;
      } else {
        byte[] plainText = kryptonite.decipher((String) object, fieldMetaData);
        Object restoredField = serdeProcessor.bytesToObject(plainText);
        return restoredField;
      }
    } catch (Exception e) {
      throw new DataException(
          "error: "
              + cipherMode
              + " of field path '"
              + matchedPath
              + "' failed unexpectedly",
          e);
    }
  }

  public List<?> processListField(List<?> list, String matchedPath) {
    return list.stream()
        .map(
            e -> {
              if (e instanceof List) return processListField((List<?>) e, matchedPath);
              if (e instanceof Map) return processMapField((Map<?, ?>) e, matchedPath);
              return processField(e, matchedPath);
            })
        .collect(Collectors.toList());
  }

  public Map<?, ?> processMapField(Map<?, ?> map, String matchedPath) {
    return map.entrySet().stream()
        .map(
            e -> {
              String pathUpdate = matchedPath + pathDelimiter + e.getKey();
              if (e.getValue() instanceof List)
                return new AbstractMap.SimpleEntry<>(
                    e.getKey(), processListField((List<?>) e.getValue(), pathUpdate));
              if (e.getValue() instanceof Map)
                return new AbstractMap.SimpleEntry<>(
                    e.getKey(), processMapField((Map<?, ?>) e.getValue(), pathUpdate));
              return new AbstractMap.SimpleEntry<>(
                  e.getKey(), processField(e.getValue(), pathUpdate));
            })
        .collect(
            LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
  }

  private FieldMetaData determineFieldMetaData(Object object, String fieldPath) {
    return Optional.ofNullable(fieldConfig.get(fieldPath))
        .map(
            fc ->
                new FieldMetaData(
                    fc.getAlgorithm()
                        .orElseGet(() -> config.getString(CipherField.CIPHER_ALGORITHM)),
                    Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""),
                    fc.getKeyName()
                        .orElseGet(() -> config.getString(CipherField.CIPHER_DATA_KEY_NAME)),
                    fc.getKeyVersion()
                        .orElseGet(() -> config.getString(CipherField.CIPHER_DATA_KEY_VERSION))))
        .orElseGet(
            () ->
                new FieldMetaData(
                    config.getString(CipherField.CIPHER_ALGORITHM),
                    Optional.ofNullable(object).map(o -> o.getClass().getName()).orElse(""),
                    config.getString(CipherField.CIPHER_DATA_KEY_NAME),
                    config.getString(CipherField.CIPHER_DATA_KEY_VERSION)));
  }
}
