package com.zero.ddd.core.helper;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2023-03-08 08:21:56
 * @Desc 些年若许,不负芳华.
 *
 */
public class CircularIterator<T> implements Iterator<T> {

    private final Iterable<T> iterable;
    private Iterator<T> iterator;
    T nextValue;

    public CircularIterator(final Collection<T> col) {
        this.iterable = Objects.requireNonNull(col);
        this.iterator = col.iterator();
        if (col.isEmpty()) {
            throw new IllegalArgumentException("CircularIterator can only be used on non-empty lists");
        }
        this.nextValue = advance();
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public T next() {
        final T next = nextValue;
        nextValue = advance();
        return next;
    }

    private T advance() {
        if (!iterator.hasNext()) {
            iterator = iterable.iterator();
        }
        return iterator.next();
    }

    public T peek() {
        return nextValue;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
