package com.github.hpgrahsl.kafka.connect.transforms.kryptonite.serdes;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KryoSerdeProcessorTest {

  @Test
  @DisplayName("serialize and deserialize with variable sized objects")
  void serializeAndDeserializeWithVariableSized() {
    KryoSerdeProcessor processor = new KryoSerdeProcessor(32, -1);

    String nullString = null;
    String small = "test";
    String large = new String(new char[100000]).replace("\0", "a");

    assertAll(
        () ->
            assertEquals(nullString, processor.bytesToObject(processor.objectToBytes(nullString))),
        () -> assertEquals(small, processor.bytesToObject(processor.objectToBytes(small))),
        () -> assertEquals(large, processor.bytesToObject(processor.objectToBytes(large))));
  }
}
