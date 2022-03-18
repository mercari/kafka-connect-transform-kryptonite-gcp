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

package com.github.hpgrahsl.kryptonite;

public class FieldMetaData {

  public static final String IDENTIFIER_DELIMITER_DEFAULT = "/versions/";

  private final String algorithm;
  private final String dataType;
  private final String keyName;
  private final String keyVersion;
  private final String delimiter;

  public FieldMetaData(String algorithm, String dataType, String keyName, String keyVersion) {
    this(algorithm, dataType, keyName, keyVersion, IDENTIFIER_DELIMITER_DEFAULT);
  }

  public FieldMetaData(
      String algorithm, String dataType, String keyName, String keyVersion, String delimiter) {
    this.algorithm = algorithm;
    this.dataType = dataType;
    this.keyName = keyName;
    this.keyVersion = keyVersion;
    this.delimiter = delimiter;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public String getAlgorithmId() {
    return Kryptonite.CIPHERNAME_ID_LUT.get(algorithm);
  }

  public String getDataType() {
    return dataType;
  }

  public String getKeyName() {
    return keyName;
  }

  public String getKeyVersion() {
    return keyVersion;
  }

  public String getDelimiter() {
    return delimiter;
  }

  public String getIdentifier() {
    return String.join(delimiter, keyName, keyVersion);
  }

  public String getIdentifier(String keyVersion) {
    return String.join(delimiter, keyName, keyVersion);
  }

  @Override
  public String toString() {
    return "FieldMetaData{"
        + "algorithm='"
        + algorithm
        + "'"
        + ", dataType='"
        + dataType
        + "'"
        + ", keyName='"
        + keyName
        + "'"
        + ", keyVersion='"
        + keyVersion
        + "'"
        + ", delimiter='"
        + delimiter
        + "'"
        + "}";
  }
}
