# Kryptonite - An SMT for Kafka Connect

Kryptonite is a turn-key ready [transformation](https://kafka.apache.org/documentation/#connect_transforms) (SMT) for [Apache Kafka®](https://kafka.apache.org/) 
to do field-level encryption/decryption of records with or without schema in data integration scenarios based on [Kafka Connect](https://kafka.apache.org/documentation/#connect).
It uses authenticated encryption with associated data ([AEAD](https://en.wikipedia.org/wiki/Authenticated_encryption)) and in particular applies 
[AES](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard) in [GCM](https://en.wikipedia.org/wiki/Galois/Counter_Mode) mode.

**This repository is forked from [hpgrahsl/kryptonite-for-kafka](https://github.com/hpgrahsl/kryptonite-for-kafka), thanks for [@hpgrahsl](https://github.com/hpgrahsl)**

## tl;dr

### Data Records without Schema

The following fictional data **record value without schema** - represented in JSON-encoded format - 
is used to illustrate a simple encrypt/decrypt scenario: 

```json5
{
  "id": "1234567890",
  "myString": "some foo bla text",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString":"hello json"},
  "myArray1": ["str_1","str_2","...","str_N"],
  "mySubDoc2": {"k1":9,"k2":8,"k3":7}
}
```

#### Encryption of selected fields

Let's assume the fields `"myString"`,`"myArray1"` and `"mySubDoc2"` of the above data record should get encrypted, 
the CipherField SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"name\":\"my-demo-secret-key\",\"version\":\"123\",\"material\":\"YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=\"}]", // key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_name": "my-demo-secret-key",
  "transforms.cipher.cipher_data_key_version": "123",  
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are 
**encrypted using the secret key** specified with the `cipher_data_key_name` and `cipher_data_key_version` parameters.

If you specify `cipher_data_keys`, then apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the secret keys renders encryption useless.
The recommended way of doing this for now is to indirectly reference secret key materials by externalizing them into a separate properties file.
Read a few details about this [here](#externalize-configuration-parameters). 
It is also possible to use the GCP Cloud KMS and Secret Manager. Please see [here](#gcp-integrations) for details.

Since the configuration parameter `field_mode` is set to 'OBJECT', complex field types are processed as a whole instead of element-wise. 

Below is an exemplary JSON-encoded record after the encryption:

```json5
{
  "id": "1234567890",
  "myString": "123#OtWbJ+VR6P6i1x9DE4FKOmsV43HOHttUjdufCjrt6SIixILy+6Bk9zBdWC4KCgeN9I2z",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString":"hello json"},
  "myArray1": "123#uWz9MODqJ0hyzXYaraEZ08S1e78ZOC0G4zeL8eZmISUpMiNsfBLDviBlWrCL2cQRbt3qNGlpKUys7/Lio9OIc0A=",
  "mySubDoc2": "123#O0AHEZ8pOccnmBHT/5kJj2QQeke3ltf8i/kJzEo/alB2sOqUooFGThBKDZA0HjdC2zz9thvB8zfjw7+fbfts6/4="
}
```

**NOTE:** Encrypted fields are always represented as **Base64-encoded strings**, 
with the **ciphertext** of the field's original values and the version number of the secret key appended to the beginning, separated by **#**.

#### Decryption of selected fields

Provided that the secret key material used to encrypt the original data record is made available to a specific sink connector, 
the CipherField SMT can be configured to decrypt the data like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "DECRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"name\":\"my-demo-secret-key\",\"version\":\"123\",\"material\":\"YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=\"}]", // key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_name": "my-demo-secret-key",
  "transforms.cipher.cipher_data_key_version": "123",  
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **decrypted 
using the secret key version that is specified and was used to encrypt the original data**.

Below is an exemplary JSON-encoded record after the decryption, which is equal to the original record:

```json5
{
  "id": "1234567890",
  "myString": "some foo bla text",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString":"hello json"},
  "myArray1": ["str_1","str_2","...","str_N"],
  "mySubDoc2": {"k1":9,"k2":8,"k3":7}
}
```

### Data Records with Schema

The following example is based on an **Avro value record** and used to illustrate a simple encrypt/decrypt scenario for data records 
with schema. The schema could be defined as:

```json5
{
    "type": "record", "fields": [
        { "name": "id", "type": "string" },
        { "name": "myString", "type": "string" },
        { "name": "myInt", "type": "int" },
        { "name": "myBoolean", "type": "boolean" },
        { "name": "mySubDoc1", "type": "record",
            "fields": [
                { "name": "myString", "type": "string" }
            ]
        },
        { "name": "myArray1", "type": { "type": "array", "items": "string"}},
        { "name": "mySubDoc2", "type": { "type": "map", "values": "int"}}
    ]
}
```

The data of one such fictional record - represented by its `Struct.toString()` output - might look as:

```text
Struct{
  id=1234567890,
  myString=some foo bla text,
  myInt=42,
  myBoolean=true,
  mySubDoc1=Struct{myString=hello json},
  myArray1=[str_1, str_2, ..., str_N],
  mySubDoc2={k1=9, k2=8, k3=7}
}
```

#### Encryption of selected fields

Let's assume the fields `"myString"`,`"myArray1"` and `"mySubDoc2"` of the above data record should get encrypted, 
the CipherField SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"name\":\"my-demo-secret-key\",\"version\":\"123\",\"material\":\"YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=\"}]", // key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_name": "my-demo-secret-key",
  "transforms.cipher.cipher_data_key_version": "123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are 
**encrypted using the secret key** specified by its id with the `cipher_data_key_name` and `cipher_data_version` parameters.

If you specify `cipher_data_keys`, then apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the secret keys renders encryption useless.
The recommended way of doing this for now is to indirectly reference secret key materials by externalizing them into a separate properties file.
Read a few details about this [here](#externalize-configuration-parameters).
It is also possible to use the GCP Cloud KMS and Secret Manager. Please see [here](#gcp-integrations) for details.

Since the configuration parameter `field_mode` is set to 'OBJECT', complex field types are processed as a whole instead of element-wise.

Below is an exemplary `Struct.toString()` output of the record after the encryption:

```text
Struct{
  id=1234567890,
  myString=123#OtWbJ+VR6P6i1x9DE4FKOmsV43HOHttUjdufCjrt6SIixILy+6Bk9zBdWC4KCgeN9I2z,
  myInt=42,
  myBoolean=true,
  mySubDoc1=Struct{myString=hello json},
  myArray1=123#uWz9MODqJ0hyzXYaraEZ08S1e78ZOC0G4zeL8eZmISUpMiNsfBLDviBlWrCL2cQRbt3qNGlpKUys7/Lio9OIc0A=,
  mySubDoc2=123#O0AHEZ8pOccnmBHT/5kJj2QQeke3ltf8i/kJzEo/alB2sOqUooFGThBKDZA0HjdC2zz9thvB8zfjw7+fbfts6/4=
}
```

**NOTE 1:** Encrypted fields are always represented as **Base64-encoded strings**,
with the **ciphertext** of the field's original values and the version number of the secret key appended to the beginning, separated by **#**.

**NOTE 2:** Obviously, in order to support this **the original schema of the data record is automatically redacted such 
that any encrypted fields can be stored as strings**, even though the original data types for the fields in question were different ones.

#### Decryption of selected fields

Provided that the secret key material used to encrypt the original data record is made available to a specific sink connector, 
the CipherField SMT can be configured to decrypt the data like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "DECRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"name\":\"my-demo-secret-key\",\"version\":\"123\",\"material\":\"YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=\"}]", // key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_name": "my-demo-secret-key",
  "transforms.cipher.cipher_data_key_version": "123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\",\"schema\": {\"type\": \"STRING\"}},{\"name\":\"myArray1\",\"schema\": {\"type\": \"ARRAY\",\"valueSchema\": {\"type\": \"STRING\"}}},{\"name\":\"mySubDoc2\",\"schema\": { \"type\": \"MAP\", \"keySchema\": { \"type\": \"STRING\" }, \"valueSchema\": { \"type\": \"INT32\"}}}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

**Take notice of the extended `field_config` parameter settings.** For decryption of schema-aware data, the SMT configuration expects 
that for each field to decrypt the original schema information is explicitly specified.
This allows to **redact the encrypted record's schema towards a compatible decrypted record's schema upfront,** 
such that the resulting plaintext field values can be stored in accordance with their original data types.   

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are 
**decrypted using the secret key id that is specified and was used to encrypt the original data**. 

Below is the decrypted data - represented by its `Struct.toString()` output - which is equal to the original record:

```text
Struct{
  id=1234567890,
  myString=some foo bla text,
  myInt=42,
  myBoolean=true,
  mySubDoc1=Struct{myString=hello json},
  myArray1=[str_1, str_2, ..., str_N],
  mySubDoc2={k1=9, k2=8, k3=7}
}
```

## Configuration Parameters

| name                                       | Description                                                                                                                                                                                                                                    | Type     | Default           | Valid values                                                                                                                                                                                        | Importance |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| cipher_data_key_name                       | secret key name to be used as default data encryption key for all fields which don't refer to a field-specific secret key name                                                                                                                 | string   |                   | non-empty string                                                                                                                                                                                    | high       |
| cipher_data_key_version                    | secret key version to be used as default data encryption key for all fields which don't refer to a field-specific secret key version                                                                                                           | string   |                   | non-empty string                                                                                                                                                                                    | high       |
| cipher_data_keys                           | JSON array with data key objects specifying the key name, key version and base64 encoded key bytes used for encryption / decryption. The key material is mandatory if the key_source=CONFIG                                                    | password |                   | JSON array holding at least one valid data key config object, e.g. <ul><li>if <em>key_source=CONFIG</em><br/>[{"identifier":"my-key-id-1234-abcd","material":"dmVyeS1zZWNyZXQta2V5JA=="}]</li></ul> | medium     |
| cipher_data_key_cache_expiry_duration      | defines the expiration duration of the secret key cache<br><strong>To be used if <em>key_source</em> is GCP_SECRET_MANAGER or GCP_SECRET_MANAGER_WITH_KMS</strong>                                                                             | long     | 24                | long value                                                                                                                                                                                          | low        |
| cipher_data_key_cache_expiry_duration_unit | defines the unit of expiration duration of the private key cache<br><strong>To be used if <em>key_source</em> is GCP_SECRET_MANAGER or GCP_SECRET_MANAGER_WITH_KMS</strong>                                                                    | string   | HOURS             | NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS                                                                                                                              | low        |
| cipher_mode                                | defines whether the data should get encrypted or decrypted                                                                                                                                                                                     | string   |                   | ENCRYPT or DECRYPT                                                                                                                                                                                  | high       |
| field_config                               | JSON array with field config objects specifying which fields together with their settings should get either encrypted / decrypted (nested field names are expected to be separated by '.' per default, or by a custom 'path_delimiter' config  | string   |                   | JSON array holding at least one valid field config object, e.g. [{"name": "my-field-abc"},{"name": "my-nested.field-xyz"}]                                                                          | high       |
| key_source                                 | defines the origin of the secret key material (currently supports keys specified in the config or the GCP Secret Manager)                                                                                                                      | string   | CONFIG            | CONFIG or GCP_SECRET_MANAGER or GCP_SECRET_MANAGER_WITH_KMS                                                                                                                                         | medium     | 
| kms_key_name                               | The GCP Cloud KMS key name for decrypting a data encryption key (DEK), if the DEK is encrypted with a key encryption key (KEK)<br><strong>To be used if <em>key_source</em> is GCP_SECRET_MANAGER_WITH_KMS</strong>                            | string   |                   | non-empty string e.g. projects/YOUR_PROJECT/locations/LOCATION/keyRings/YOUR_KEY_RING/cryptoKeys/YOUR_KEY                                                                                           | medium     |
| field_mode                                 | defines how to process complex field types (maps, lists, structs), either as full objects or element-wise                                                                                                                                      | string   | ELEMENT           | ELEMENT or OBJECT                                                                                                                                                                                   | medium     |
| cipher_algorithm                           | cipher algorithm used for data encryption (currently supports only one AEAD cipher: AES/GCM/NoPadding)                                                                                                                                         | string   | AES/GCM/NoPadding | AES/GCM/NoPadding                                                                                                                                                                                   | low        |
| cipher_text_encoding                       | defines the encoding of the resulting ciphertext bytes (currently only supports 'base64')                                                                                                                                                      | string   | base64            | base64                                                                                                                                                                                              | low        |
| path_delimiter                             | path delimiter used as field name separator when referring to nested fields in the input record                                                                                                                                                | string   | .                 | non-empty string                                                                                                                                                                                    | low        |
| kryo_output_buffer_size                    | Initial buffer size for kryo to serialize.                                                                                                                                                                                                     | int      | 32                | int value                                                                                                                                                                                           | low        |
| kryo_output_buffer_size_max                | Maximum buffer size for kryo to serialize. Default -1 corresponds to no upper limit (up to Integer.MAX_VALUE - 8 technically).                                                                                                                 | int      | -1                | int value                                                                                                                                                                                           | low        |

### Externalize configuration parameters

The problem with directly specifying configuration parameters which contain sensitive data, such as secret key materials, 
is that they are exposed via Kafka Connect's REST API. 
This means for connect clusters that are shared among teams the configured secret key materials would leak, which is of course unacceptable. 
The way to deal with this for now, is to indirectly reference such configuration parameters from external property files.

This approach can be used to configure any kind of sensitive data such as KMS-specific client authentication settings, 
in case the secret keys aren't sourced from the config directly but rather retrieved from an external KMS such as Azure Key Vault.

Below is a quick example of how such a configuration would look like:

1. Before you can make use of configuration parameters from external sources you have to customize your Kafka Connect worker configuration 
   by adding the following two settings:

```
connect.config.providers=file
connect.config.providers.file.class=org.apache.kafka.common.config.provider.FileConfigProvider
```

2. Then you create the external properties file e.g. `classified.properties` which contains the secret key materials. 
   This file needs to be available on all your Kafka Connect workers which you want to run Kryptonite on. 
   Let's pretend the file is located at path `/secrets/kryptonite/classified.properties` on your worker nodes:

```properties
cipher_data_keys=[{"name":"my-demo-secret-key","version":"123","material":"YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="}]
```

3. Finally, you simply reference this file and the corresponding key of the property therein, from your SMT configuration like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "${file:/secrets/kryptonite/classified.properties:cipher_data_keys}",
  "transforms.cipher.cipher_data_key_name": "my-demo-secret-key-123",
  "transforms.cipher.cipher_data_key_version": "123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

In case you want to learn more about configuration parameter externalization there is e.g. this nice 
[blog post](https://debezium.io/blog/2019/12/13/externalized-secrets/) from the Debezium team showing 
how to externalize username and password settings using a docker-compose example.

### GCP Integrations

You can use the GCP Secret Manager to manage your secret keys.
The CipherField SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.key_source": "GCP_SECRET_MANAGER",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_key_name": "projects/YOUR_PROJECT_NUMBER/secrets/YOUR_SECRET_NAME",
  "transforms.cipher.cipher_data_key_version": "3",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

Specify `GCP_SECRET_MANAGER` for `key_source`, and specify the secret name and version of the Secret Manager to be used by default for 
`cipher_data_key_name` and `cipher_data_key_version`.  It is assumed that the Secret Manager stores base64-encoded secret keys.
It retrieves all valid versions of the secret specified for default use at startup and caches them in memory.
Cache expiration can be set with `cipher_data_key_cache_expiry_duration` and `cipher_data_key_cache_expiry_duration_unit`.
The default is 24 hours. When the cache expires, the secret is evicted and automatically cached again the next time it is accessed.
When encrypting, the default secret version is used, or the matching secret version if specified in `field_config`.
When decrypting, the secret key that matches the version prefix of the encrypted data will be used automatically. 
If there is no version number prefix, the default or the secret specified in `field_config` will be used.

Rotating the secret key is simply a matter of registering a new secret version and updating the secret version used by default.
Since the secret version is automatically selected for decryption, data encrypted with an older version of the secret key can be decrypted, 
unless the older version of the secret is disabled.

Secret keys stored in the Secret Manager can also be encrypted with the Cloud KMS. 
Use the Cloud KMS for the key encryption key (KEK) and the Secret Manager for the data encryption key (DEK). 
See [Envelope encryption](https://cloud.google.com/kms/docs/envelope-encryption) for details.
The CipherField SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.key_source": "GCP_SECRET_MANAGER_WITH_KMS",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.kms_key_name": "projects/YOUR_PROJECT/locations/YOUR_LOCATION/keyRings/YOUR_KEY_RING/cryptoKeys/YOUR_KEY",
  "transforms.cipher.cipher_data_key_name": "projects/YOUR_PROJECT_NUMBER/secrets/YOUR_SECRET_NAME",
  "transforms.cipher.cipher_data_key_version": "3",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

Specify `GCP_SECRET_MANAGER_WITH_KMS` for `key_source`, and specify the name of the Cloud KMS key for `kms_key_name`.
The basic behavior is the same as when `GCP_SECRET_MANAGER` is specified, 
but the Cloud KMS will decrypt the key when it retrieves the key stored in the Secret Manager at startup.

This can be used when you do not want to store the raw secret key in the Secret Manager. 
Also, depending on the configuration, it is possible to automate the key encription key (KEK) rotation.

### Build, installation / deployment

This project can be built from source via Maven, or you can download the package from the 
GitHub [release page](https://github.com/mercari/kafka-connect-transform-kryptonite-gcp/releases).

In order to deploy it you simply put the jar into a _'plugin path'_ that is configured to be scanned by your Kafka Connect worker nodes.

After that, configure Kryptonite as transformation for any of your source / sink connectors, sit back and relax! 
Happy _'binge watching'_ plenty of ciphertexts ;-)

### Cipher algorithm specifics

Kryptonite currently provides a single cipher algorithm, namely, AES in GCM mode. 
It offers so-called _authenticated encryption with associated data_ (AEAD). 

By design, every application of Kryptonite on a specific record field results in different ciphertexts for one and the same plaintext. 
This is in general not only desirable but very important to make attacks harder. 
However, in the context of Kafka Connect records this has an unfavorable consequence for source connectors. 
**Applying the SMT on a source record's key would result in a 'partition mix-up'** 
because records with the same original plaintext key would end up in different topic partitions. 
In other words, **do NOT(!) use Kryptonite for 
source record keys** at the moment. There are plans in place to do away with this restriction and extend Kryptonite with a deterministic mode. 
This could then safely support the encryption of record keys while at the same time keep topic partitioning and record ordering intact.

## Contribution

Please read the CLA carefully before submitting your contribution to Mercari. Under any circumstances, by submitting your contribution, you are deemed to accept and agree to be bound by the terms and conditions of the CLA.

https://www.mercari.com/cla/

## License Information

This project is licensed according to [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)

```
Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

