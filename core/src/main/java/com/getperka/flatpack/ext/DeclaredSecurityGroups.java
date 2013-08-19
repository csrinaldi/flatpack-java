package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.listForAny;
import static com.getperka.flatpack.util.FlatPackCollections.mapForIteration;
import static com.getperka.flatpack.util.FlatPackCollections.setForLookup;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import com.getperka.flatpack.security.InheritGroups;

/**
 * A summary of all security groups both declared and inherited by an entity type.
 */
public class DeclaredSecurityGroups implements Iterable<SecurityGroup> {

  private static final DeclaredSecurityGroups EMPTY = new DeclaredSecurityGroups() {
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
   * Returns an always-empty {@link DeclaredSecurityGroups} instance that never resolves.
   */
  public static DeclaredSecurityGroups empty() {
    return EMPTY;
  }

  private Map<String, SecurityGroup> declared = Collections.emptyMap();
  private Map<Property, DeclaredSecurityGroups> inherited = Collections.emptyMap();
  private Map<String, SecurityGroup> resolved = new ConcurrentHashMap<String, SecurityGroup>();
  @Inject
  private SecurityGroups securityGroups;

  /**
   * Requires injection.
   */
  protected DeclaredSecurityGroups() {}

  /**
   * Copy initializer. The collections returned by the newly-created instance are guaranteed to be
   * mutable copies of the original data.
   */
  public void copyFrom(DeclaredSecurityGroups other) {
    declared = sortedMapForIteration();
    declared.putAll(other.declared);
    inherited = mapForIteration();
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
  public Map<Property, DeclaredSecurityGroups> getInherited() {
    return inherited;
  }

  public boolean isEmpty() {
    return declared.isEmpty() && inherited.isEmpty();
  }

  /**
   * Iterate over both declared and inherited {@link SecurityGroup} instances. In the case of
   * non-trivial group inheritance, this iterator is guaranteed to return any particular
   * SecurityGroup at most once.
   */
  @Override
  public Iterator<SecurityGroup> iterator() {
    return iterator(Collections.<Property> emptyList());
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

    toReturn = securityGroups.resolve(this, name);
    if (toReturn != null) {
      resolved.put(name, toReturn);
    }
    return toReturn;
  }

  void setDeclared(Map<String, SecurityGroup> declared) {
    this.declared = declared;
  }

  void setInherited(Map<Property, DeclaredSecurityGroups> inherited) {
    this.inherited = inherited;
  }

  /**
   * Returns an iterator if the current {@link DeclaredSecurityGroups} has not yet been visited.
   * This method correctly handles self-referential group declarations.
   */
  private Iterator<SecurityGroup> iterator(final List<Property> prefix) {
    // If we're entering a cycle, return an empty iterator
    if (prefix.size() == 4) {
      return Collections.<SecurityGroup> emptyList().iterator();
    }
    return new Iterator<SecurityGroup>() {
      private Iterator<SecurityGroup> current = declared.values().iterator();
      private Iterator<Map.Entry<Property, DeclaredSecurityGroups>> more = inherited.entrySet()
          .iterator();
      private SecurityGroup next;
      private boolean hasNext;
      private Set<SecurityGroup> seen = setForLookup();

      {
        // Initialize the state of this iterator
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
            // Prepend any current prefix
            if (!prefix.isEmpty()) {
              maybeNext = new SecurityGroup(maybeNext, prefix);
            }
          } else if (more.hasNext()) {
            // Otherwise, switch to the next inherited SecurityGroups and restart
            Map.Entry<Property, DeclaredSecurityGroups> entry = more.next();
            List<Property> nextPrefix = listForAny();
            nextPrefix.addAll(prefix);
            nextPrefix.add(entry.getKey());
            current = entry.getValue().iterator(nextPrefix);
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
}
