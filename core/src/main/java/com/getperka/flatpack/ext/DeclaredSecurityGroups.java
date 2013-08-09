package com.getperka.flatpack.ext;

import static com.getperka.flatpack.util.FlatPackCollections.setForLookup;
import static com.getperka.flatpack.util.FlatPackCollections.sortedMapForIteration;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import com.getperka.flatpack.HasUuid;
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
  private Map<String, DeclaredSecurityGroups> inherited = Collections.emptyMap();
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
  public Map<String, DeclaredSecurityGroups> getInherited() {
    return inherited;
  }

  public boolean isEmpty() {
    return declared.isEmpty() && inherited.isEmpty();
  }

  @Override
  public Iterator<SecurityGroup> iterator() {
    return new Iterator<SecurityGroup>() {
      private Iterator<SecurityGroup> current = declared.values().iterator();
      private Iterator<DeclaredSecurityGroups> more = inherited.values().iterator();
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
    
    toReturn = securityGroups.resolve(this, name);
    resolved.put(name, toReturn);
    return toReturn;
  }

  void setDeclared(Map<String, SecurityGroup> declared) {
    this.declared = declared;
  }

  void setInherited(Map<String, DeclaredSecurityGroups> inherited) {
    this.inherited = inherited;
  }
}
