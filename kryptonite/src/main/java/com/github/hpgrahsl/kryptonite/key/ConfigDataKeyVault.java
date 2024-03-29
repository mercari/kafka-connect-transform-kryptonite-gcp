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

package com.github.hpgrahsl.kryptonite.key;

import java.util.Map;

public class ConfigDataKeyVault extends KeyVault {

  private final Map<String, byte[]> keys;

  public ConfigDataKeyVault(Map<String, byte[]> keys) {
    this(keys, new NoOpKeyStrategy());
  }

  public ConfigDataKeyVault(Map<String, byte[]> keys, KeyStrategy keyStrategy) {
    super(keyStrategy);
    this.keys = keys;
  }

  @Override
  public byte[] readKey(String identifier) {
    byte[] keyBytes = keys.get(identifier);
    if (keyBytes == null) {
      throw new KeyNotFoundException(
          "could not find key for identifier '"
              + identifier
              + "' in "
              + ConfigDataKeyVault.class.getName()
              + " key vault");
    }
    return keyStrategy.processKey(keyBytes, identifier);
  }

  @Override
  public void close() {}
}
