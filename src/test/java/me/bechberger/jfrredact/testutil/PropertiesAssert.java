package me.bechberger.jfrredact.testutil;

import me.bechberger.jfrredact.config.PropertyConfig;
import org.assertj.core.api.AbstractAssert;

/**
 * Test helper assertions for PropertyConfig.
 */
public final class PropertiesAssert extends AbstractAssert<PropertiesAssert, PropertyConfig> {

    public PropertiesAssert(PropertyConfig actual) {
        super(actual, PropertiesAssert.class);
    }

    public static PropertiesAssert assertThatProperties(PropertyConfig actual) {
        return new PropertiesAssert(actual);
    }

    public PropertiesAssert matches(String fieldName) {
        isNotNull();
        try {
            java.lang.reflect.Method m = actual.getClass().getMethod("matches", String.class);
            Object res = m.invoke(actual, fieldName);
            if (!(res instanceof Boolean) || !((Boolean) res)) {
                failWithMessage("Expected properties to match '%s' but it did not", fieldName);
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            failWithMessage("Failed to evaluate matches('%s'): %s", fieldName, e.getMessage());
        }
        return this;
    }
}