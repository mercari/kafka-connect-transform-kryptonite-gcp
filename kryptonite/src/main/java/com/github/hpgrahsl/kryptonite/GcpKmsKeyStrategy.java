package com.github.hpgrahsl.kryptonite;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpKmsKeyStrategy extends KeyStrategy {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcpKmsKeyStrategy.class);

  private final KeyManagementServiceClient client;
  private final CryptoKeyName keyName;

  public GcpKmsKeyStrategy(String keyName) throws IOException {
    this.client = KeyManagementServiceClient.create();
    this.keyName = CryptoKeyName.parse(keyName);
  }

  @Override
  byte[] processKey(byte[] origKeyBytes, String identifier) {
    LOGGER.info("Process key: " + identifier);
    LOGGER.info("KEK name: " + keyName);
    DecryptResponse resp = client.decrypt(keyName, ByteString.copyFrom(origKeyBytes));
    return resp.getPlaintext().toByteArray();
  }
}
