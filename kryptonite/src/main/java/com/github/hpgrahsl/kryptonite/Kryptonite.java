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

import com.github.hpgrahsl.kryptonite.crypto.AesGcmNoPadding;
import com.github.hpgrahsl.kryptonite.crypto.CryptoAlgorithm;
import com.github.hpgrahsl.kryptonite.key.KeyVault;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class Kryptonite {

  public static final String VERSION_DELIMITER_DEFAULT = "#";

  @SuppressWarnings("serial")
  public static final Map<String, String> CIPHERNAME_ID_LUT =
      new LinkedHashMap<String, String>() {
        {
          put("AES/GCM/NoPadding", "01");
        }
      };

  @SuppressWarnings("serial")
  public static final Map<String, CryptoAlgorithm> ID_CRYPTOALGORITHM_LUT =
      new LinkedHashMap<String, CryptoAlgorithm>() {
        {
          put("01", new AesGcmNoPadding());
        }
      };

  private final KeyVault keyVault;
  private final String delimiter;

  public Kryptonite(KeyVault keyVault) {
    this(keyVault, VERSION_DELIMITER_DEFAULT);
  }

  public Kryptonite(KeyVault keyVault, String delimiter) {
    this.keyVault = keyVault;
    this.delimiter = delimiter;
  }

  private byte[] cipher(byte[] plainText, String algorithmId, String identifier) {
    try {
      return ID_CRYPTOALGORITHM_LUT
          .get(algorithmId)
          .cipher(plainText, keyVault.readKey(identifier));
    } catch (Exception e) {
      throw new DataException(e.getMessage(), e);
    }
  }

  public String cipher(byte[] plainText, FieldMetaData metadata) {
    String cipherText =
        Base64.getEncoder()
            .encodeToString(cipher(plainText, metadata.getAlgorithmId(), metadata.getIdentifier()));
    return String.join(delimiter, metadata.getKeyVersion(), cipherText);
  }

  private byte[] decipher(byte[] cipherText, String algorithmId, String identifier) {
    try {
      return ID_CRYPTOALGORITHM_LUT
          .get(algorithmId)
          .decipher(cipherText, keyVault.readKey(identifier));
    } catch (Exception e) {
      throw new DataException(e.getMessage(), e);
    }
  }

  public byte[] decipher(String cipherText, FieldMetaData metadata) {
    String[] splitText = cipherText.split(delimiter);
    if (splitText.length == 1) {
      byte[] decoded = Base64.getDecoder().decode(splitText[0]);
      return decipher(decoded, metadata.getAlgorithmId(), metadata.getIdentifier());
    } else if (splitText.length == 2) {
      byte[] decoded = Base64.getDecoder().decode(splitText[1]);
      return decipher(decoded, metadata.getAlgorithmId(), metadata.getIdentifier(splitText[0]));
    } else {
      throw new DataException("Illegal cipher text format.");
    }
  }

  public void close() {
    this.keyVault.close();
  }
}
