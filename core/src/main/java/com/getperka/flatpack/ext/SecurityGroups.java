package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.setForLookup;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.getperka.flatpack.security.InheritGroups;

/**
 * A summary of all security groups both declared and inherited by an entity type.
 */
public class SecurityGroups implements Iterable<SecurityGroup> {

  private static final SecurityGroups EMPTY = new SecurityGroups() {
    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public SecurityGroup resolve(String name) {
      return null;
    }
  };

  /**
   * Returns an always-empty {@link SecurityGroups} instance that never resolves.
   */
  public static SecurityGroups empty() {
    return EMPTY;
  }

  private Map<String, SecurityGroup> declared = Collections.emptyMap();
  private Map<String, SecurityGroups> inherited = Collections.emptyMap();
  private Map<String, SecurityGroup> resolved = new ConcurrentHashMap<String, SecurityGroup>();

  public SecurityGroups() {}

  /**
   * Copy constructor. The collections returned by the newly-created instance are guaranteed to be
   * mutable copies of the original data.
   */
  public SecurityGroups(SecurityGroups other) {
    declared = sortedMapForIteration();
    declared.putAll(other.declared);
    inherited = sortedMapForIteration();
    inherited.putAll(other.inherited);
  }

  /**
   * The security groups declared by the entity type or inherited from its supertype.
   */
  public Map<String, SecurityGroup> getDeclared() {
    return declared;
  }

  /**
   * The security groups inherited by the entity type via an {@link InheritGroups} annotation. The
   * map key is the group path prefix.
   */
  public Map<String, SecurityGroups> getInherited() {
    return inherited;
  }

  public boolean isEmpty() {
    return declared.isEmpty() && inherited.isEmpty();
  }

  @Override
  public Iterator<SecurityGroup> iterator() {
    return new Iterator<SecurityGroup>() {
      private Iterator<SecurityGroup> current = declared.values().iterator();
      private Iterator<SecurityGroups> more = inherited.values().iterator();
      private SecurityGroup next;
      private boolean hasNext;
      private Set<SecurityGroup> seen = setForLookup();

      {
        pump();
      }

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public SecurityGroup next() {
        if (!hasNext) {
          throw new NoSuchElementException();
        }
        SecurityGroup toReturn = next;
        pump();
        return toReturn;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

      /**
       * Advance the state of the iterator, possibly delegating to inherited SecurityGroups.
       */
      private void pump() {
        SecurityGroup maybeNext;
        while (true) {
          if (current.hasNext()) {
            // Try consuming from the current iterator
            maybeNext = current.next();
          } else if (more.hasNext()) {
            // Otherwise, switch to the next inherited SecurityGroups and restart
            current = more.next().iterator();
            continue;
          } else {
            // Nothing left to return
            hasNext = false;
            return;
          }

          // Ensure that a unique SecurityGroup will be returned
          if (seen.add(maybeNext)) {
            next = maybeNext;
            hasNext = true;
            return;
          }
        }
      }
    };
  }

  /**
   * Returns the {@link SecurityGroup} with the given relative name, or
   * {@code SecurityGroup#empty()} if one is not defined.
   * 
   * @param name the name of the security group, which may be a chained reference (e.g.
   *          {@code manager.director}).
   */
  public SecurityGroup resolve(String name) {
    SecurityGroup toReturn = resolved.get(name);
    if (toReturn != null) {
      return toReturn;
    }

    try {
      if (SecurityGroup.ALL.equals(name)) {
        toReturn = SecurityGroup.all();
        return toReturn;
      }

      if (SecurityGroup.EMPTY.equals(name)) {
        toReturn = SecurityGroup.empty();
        return toReturn;
      }

      // If it's a dotted name, look in the inherited groups
      int idx = name.indexOf('.');
      if (idx != -1) {
        SecurityGroups next = inherited.get(name.substring(0, idx));
        toReturn = next == null ? null : next.resolve(name.substring(idx + 1));
        return toReturn;
      }

      // Look for groups declared on the entity type
      toReturn = declared.get(name);
      if (toReturn != null) {
        return toReturn;
      }

      // Look for inherited groups with the same simple name
      for (SecurityGroups g : inherited.values()) {
        toReturn = g.resolve(name);
        if (toReturn != null) {
          return toReturn;
        }
      }

      toReturn = SecurityGroup.empty();
      return toReturn;
    } finally {
      resolved.put(name, toReturn);
    }
  }

  void setDeclared(Map<String, SecurityGroup> declared) {
    this.declared = declared;
  }

  void setInherited(Map<String, SecurityGroups> inherited) {
    this.inherited = inherited;
  }
}
