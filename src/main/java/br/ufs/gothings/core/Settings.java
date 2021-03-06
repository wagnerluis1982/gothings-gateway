package br.ufs.gothings.core;

import br.ufs.gothings.core.util.AbstractKey;
import org.apache.commons.lang3.Validate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * @author Wagner Macedo
 */
public class Settings {
    private static final Map<String, Key<?>> globalKeys = new HashMap<>();

    public static final Key<Integer> SERVER_PORT = registerGlobalKey("server.port", Integer.class, port -> port > 0);

    private final AtomicBoolean locked;
    private final Map<String, Object> properties = new HashMap<>();

    public Settings(AtomicBoolean locked) {
        this.locked = locked;
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T get(String name) {
        return (T) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T get(Key<T> key) {
        return (T) properties.get(key.getKeyId());
    }

    public synchronized <T> void put(String name, T value) {
        writeCheck();
        final Key<T> key = getKey(name);
        put(key, value);
    }

    public synchronized <T> void put(Key<T> key, T value) {
        writeCheck();
        if (value == null) {
            properties.remove(key.getKeyId());
        } else if (!key.validate(value)) {
            throw new IllegalArgumentException("value `" + value + "` didn't pass in the validation");
        }
        properties.put(key.getKeyId(), value);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> Key<T> getKey(final String name) {
        Key<T> key = (Key<T>) globalKeys.get(name);
        if (key == null) {
            key = (Key<T>) localKeys.get(name);
        }
        if (key == null) {
            throw new IllegalArgumentException("the key '" + name + "' is not registered");
        }
        return key;
    }

    private synchronized void writeCheck() {
        Validate.validState(!locked.get(), "settings locked for writing");
    }

    private synchronized static <T> Key<T> registerGlobalKey(String name, final Class<T> cls, Predicate<T> validator) {
        Validate.isTrue(!globalKeys.containsKey(name), "the global key '%s' is already registered", name);

        final Key<T> key = new Key<>(name, cls, validator);
        globalKeys.put(name, key);
        return key;
    }

    private final Map<String, Key<?>> localKeys = new HashMap<>();

    public synchronized <T> Key<T> registerKey(final String name, final Class<T> cls, final Predicate<T> validator) {
        Validate.notNull(name, "name");
        Validate.notNull(cls, "cls");
        Validate.isTrue(!globalKeys.containsKey(name), "the key '%s' cannot be used because is a global key", name);
        Validate.isTrue(!localKeys.containsKey(name), "the key '%s' is already registered", name);

        final Key<T> key = new Key<>(name, cls, validator);
        localKeys.put(name, key);
        return key;
    }

    public static final class Key<T> extends AbstractKey<String, T> {
        protected Key(final String keyId, final Class<T> classType, final Predicate<T> validator) {
            super(keyId, classType, validator);
        }
    }
}
