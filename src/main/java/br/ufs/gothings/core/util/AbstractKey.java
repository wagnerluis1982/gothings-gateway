package br.ufs.gothings.core.util;

import org.apache.commons.lang3.ClassUtils;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Wagner Macedo
 */
public abstract class AbstractKey<K, T> {
    private final K keyId;
    private final Class<T> classType;
    private final Predicate<T> validator;
    private final Supplier<Collection<T>> collectionSupplier;

    protected AbstractKey(final K keyId, final Class<T> classType, final Predicate<T> validator) {
        this(keyId, classType, validator, null);
    }

    @SuppressWarnings("unchecked")
    protected AbstractKey(final K keyId, final Class<T> classType, final Predicate<T> validator,
                          final Supplier<Collection<T>> collectionSupplier)
    {
        this.keyId = keyId;
        this.classType = (Class<T>) ClassUtils.primitiveToWrapper(classType);
        this.validator = validator;
        this.collectionSupplier = collectionSupplier;
    }

    public final K getKeyId() {
        return keyId;
    }

    public final Class<T> getClassType() {
        return classType;
    }

    public final boolean validate(T value) {
        if (!classType.isInstance(value)) {
            throw new ClassCastException("value is not a type of " + classType);
        }
        if (validator == null) {
            return true;
        }
        try {
            return validator.test(value);
        } catch (Exception e) {
            return false;
        }
    }

    public final Collection<T> newCollection() {
        return collectionSupplier != null ? collectionSupplier.get() : null;
    }

    public final boolean isCollection() {
        return collectionSupplier != null;
    }
}
