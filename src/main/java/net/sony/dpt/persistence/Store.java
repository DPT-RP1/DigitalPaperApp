package net.sony.dpt.persistence;

public interface Store<T> {
    void store(T t);
    T retrieve();
}
