package com.getperka.flatpack.ext;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.getperka.flatpack.PackVisitor;

public abstract class VisitorContext<T> {
  public static class ArrayContext<T> extends VisitorContext<T> {
    private T[] array;
    private int index;

    public void acceptArray(PackVisitor visitor, T[] array, Codex<T> codex)
    {
      this.array = array;
      index = 0;
      for (int j = array.length; index < j; index++) {
        codex.accept(visitor, array[index], this);
      }
    }

    @Override
    public boolean canReplace() {
      return true;
    }

    @Override
    public void replace(T newValue) {
      array[index] = newValue;
    }

  }

  public static class ImmutableContext<T> extends VisitorContext<T> {
    public void acceptImmutable(PackVisitor visitor, T value, Codex<T> codex) {
      codex.accept(visitor, value, this);
    }
  }

  public static class IterableContext<T> extends VisitorContext<T> {
    private Iterator<T> iterator;

    public void acceptIterable(PackVisitor visitor, Iterable<T> iterable, Codex<T> codex)
    {
      iterator = iterable.iterator();
      while (iterator.hasNext()) {
        codex.accept(visitor, iterator.next(), this);
      }
    }

    @Override
    public boolean canRemove() {
      return true;
    }

    @Override
    public void remove() {
      markRemoved();
      iterator.remove();
    }
  }

  public static class ListContext<T> extends VisitorContext<T> {
    private ListIterator<T> iterator;

    public void acceptList(PackVisitor visitor, List<T> list, Codex<T> codex) {
      iterator = list.listIterator();
      while (iterator.hasNext()) {
        codex.accept(visitor, iterator.next(), this);
      }
    }

    @Override
    public boolean canInsert() {
      return true;
    }

    @Override
    public boolean canRemove() {
      return true;
    }

    @Override
    public boolean canReplace() {
      return true;
    }

    @Override
    public void insertAfter(T newValue) {
      markInserted();
      iterator.add(newValue);
      iterator.next();
    }

    @Override
    public void insertBefore(T newValue) {
      markInserted();
      boolean didBackUp = false;
      if (iterator.hasPrevious()) {
        didBackUp = true;
        iterator.previous();
      }
      iterator.add(newValue);
      iterator.next();
      if (didBackUp) {
        iterator.next();
      }
    }

    @Override
    public void remove() {
      markRemoved();
      iterator.remove();
    }

    @Override
    public void replace(T newValue) {
      markReplaced();
      iterator.set(newValue);
    }
  }

  public static class SingletonContext<T> extends VisitorContext<T> {
    private T value;

    public void acceptSingleton(PackVisitor visitor, T singleton, Codex<T> codex) {
      codex.accept(visitor, singleton, this);
    }

    @Override
    public boolean canReplace() {
      return true;
    }

    public T getValue() {
      return value;
    }

    @Override
    public void replace(T newValue) {
      markReplaced();
      this.value = newValue;
    }
  }

  private boolean inserted;
  private boolean removed;
  private boolean replaced;

  public boolean canInsert() {
    return false;
  }

  public boolean canRemove() {
    return false;
  }

  public boolean canReplace() {
    return false;
  }

  public boolean didInsert() {
    return inserted;
  }

  public boolean didRemove() {
    return removed;
  }

  public boolean didReplace() {
    return replaced;
  }

  public void insertAfter(T newValue) {
    throw new UnsupportedOperationException();
  }

  public void insertBefore(T newValue) {
    throw new UnsupportedOperationException();
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }

  public void replace(T newValue) {
    throw new UnsupportedOperationException();
  }

  protected void markInserted() {
    inserted = true;
  }

  protected void markRemoved() {
    removed = true;
    replaced = false;
  }

  protected void markReplaced() {
    removed = false;
    replaced = true;
  }
}