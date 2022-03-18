package com.github.hpgrahsl.kafka.connect.transforms.kryptonite.validators;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.config.ConfigDef.Validator;
import org.apache.kafka.common.config.ConfigException;

public class TimeUnitValidator implements Validator {

  @Override
  public void ensureValid(String name, Object o) {
    try {
      TimeUnit.valueOf((String) o);
    } catch (IllegalArgumentException exc) {
      throw new ConfigException(name, o, "Must be one of " + Arrays.toString(TimeUnit.values()));
    }
  }

  @Override
  public String toString() {
    return Arrays.toString(TimeUnit.values());
  }
}
