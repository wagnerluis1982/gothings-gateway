package br.ufs.gothings.core.message;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Wagner Macedo
 */
public class ComplexHeader<T> implements Iterable<T> {
    private final Collection<T> values;

    public ComplexHeader(Collection<T> values) {
        this.values = values;
    }

    public boolean add(T t) {
        return values.add(t);
    }

    public boolean addAll(Collection<? extends T> c) {
        return values.addAll(c);
    }

    public boolean remove(Object o) {
        return values.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        return values.removeAll(c);
    }

    public void clear() {
        values.clear();
    }

    @Override
    public Iterator<T> iterator() {
        return values.iterator();
    }
}
