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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FieldConfig {

  private String name;
  private String algorithm;
  private String keyName;
  private String keyVersion;
  private Map<String, Object> schema;

  public FieldConfig() {}

  public FieldConfig(
      String name,
      String algorithm,
      String keyName,
      String keyVersion,
      Map<String, Object> schema) {
    this.name = Objects.requireNonNull(name, "field config's name must not be null");
    this.algorithm = algorithm;
    this.keyName = keyName;
    this.keyVersion = keyVersion;
    this.schema = schema;
  }

  public String getName() {
    return name;
  }

  public Optional<String> getAlgorithm() {
    return Optional.ofNullable(algorithm);
  }

  public Optional<String> getKeyName() {
    return Optional.ofNullable(keyName);
  }

  public Optional<String> getKeyVersion() {
    return Optional.ofNullable(keyVersion);
  }

  public Optional<Map<String, Object>> getSchema() {
    return Optional.ofNullable(schema);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FieldConfig)) {
      return false;
    }
    FieldConfig that = (FieldConfig) o;
    return Objects.equals(name, that.name)
        && Objects.equals(algorithm, that.algorithm)
        && Objects.equals(keyName, that.keyName)
        && Objects.equals(keyVersion, that.keyVersion)
        && Objects.equals(schema, that.schema);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, algorithm, keyName, keyVersion, schema);
  }

  @Override
  public String toString() {
    return "FieldConfig{"
        + "name='"
        + name
        + "'"
        + ", algorithm='"
        + algorithm
        + "'"
        + ", keyName='"
        + keyName
        + "'"
        + ", keyVersion='"
        + keyVersion
        + "'"
        + ", schema="
        + schema
        + '}';
  }
}
