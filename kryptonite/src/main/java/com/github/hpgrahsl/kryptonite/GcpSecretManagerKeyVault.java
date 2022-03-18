package com.github.hpgrahsl.kryptonite;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient.ListSecretVersionsPagedResponse;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretVersion.State;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpSecretManagerKeyVault extends KeyVault {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcpSecretManagerKeyVault.class);

  private final SecretManagerServiceClient client;
  private final SecretName secretName;
  private final LoadingCache<String, byte[]> secretCache;

  public GcpSecretManagerKeyVault(String secretName, String keyName) throws IOException {
    this(secretName, new GcpKmsKeyStrategy(keyName), 24L, TimeUnit.HOURS.name());
  }

  public GcpSecretManagerKeyVault(String secretName, KeyStrategy strategy) throws IOException {
    this(secretName, strategy, 24L, TimeUnit.HOURS.name());
  }

  public GcpSecretManagerKeyVault(
      String secretName, KeyStrategy strategy, long duration, String unit) throws IOException {
    super(strategy);
    this.client = SecretManagerServiceClient.create();
    this.secretName = SecretName.parse(secretName);
    this.secretCache =
        Caffeine.newBuilder()
            .expireAfterWrite(duration, TimeUnit.valueOf(unit))
            .evictionListener(
                new RemovalListener<String, byte[]>() {
                  @Override
                  public void onRemoval(
                      @Nullable String s,
                      byte @Nullable [] bytes,
                      @NonNull RemovalCause removalCause) {
                    if (bytes != null) {
                      Arrays.fill(bytes, (byte) 0);
                    }
                  }
                })
            .build(key -> accessSecretVersion(key));
    initKeys();
  }

  private void initKeys() {
    ListSecretVersionsPagedResponse resp = client.listSecretVersions(secretName);
    resp.iterateAll()
        .forEach(
            version -> {
              if (version.getState() == State.ENABLED) {
                final String name = version.getName();
                LOGGER.info("Init key: " + name);
                secretCache.get(name);
              }
            });
  }

  private byte[] accessSecretVersion(String identifier) {
    AccessSecretVersionResponse resp = client.accessSecretVersion(identifier);
    final String key = resp.getPayload().getData().toStringUtf8();
    final byte[] keyBytes = Base64.getDecoder().decode(key);
    return keyStrategy.processKey(keyBytes, identifier);
  }

  @Override
  byte[] readKey(String identifier) {
    byte[] keyBytes = secretCache.get(identifier);
    if (keyBytes == null) {
      LOGGER.info("Read key: " + identifier);
      keyBytes = accessSecretVersion(identifier);
    }
    return keyBytes;
  }
}
