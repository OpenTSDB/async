/*
 * Copyright 2010 StumbleUpon, Inc.
 *
 * This library is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.stumbleupon.async;

import java.util.Collection;
import java.util.ArrayList;

/**
 * Groups multiple {@link Deferred}s into a single one.
 * <p>
 * This is just a helper class, see {@link Deferred#group} for more details.
 */
final class DeferredGroup<T> {

  /**
   * The Deferred we'll callback when all Deferreds in the group have been
   * called back.
   */
  private final Deferred<ArrayList<Object>> parent =
    new Deferred<ArrayList<Object>>();

  /** How many results do we expect?  */
  private final short nresults;

  /**
   * All the results for each Deferred we're grouping.
   * Need to acquires this' monitor before changing.
   */
  private final ArrayList<Object> results;

  /**
   * Constructor.
   * @param deferreds All the {@link Deferred}s we want to group.
   */
  public DeferredGroup(final Collection<Deferred<T>> deferreds) {
    nresults = (short) deferreds.size();
    results = new ArrayList<Object>(nresults);

    if (nresults == 0) {
      parent.callback(results);
      return;
    }

    @SuppressWarnings("unchecked")
    final Callback<T, T> notify = new Callback() {
      public Object call(final Object arg) {
        recordCompletion(arg);
        return arg;
      }
      public String toString() {
        return "notify DeferredGroup@" + DeferredGroup.super.hashCode();
      }
    };

    for (final Deferred<T> d : deferreds) {
      d.addBoth(notify);
    }
  }

  /**
   * Returns the parent {@link Deferred} of the group.
   */
  public Deferred<ArrayList<Object>> getDeferred() {
    return parent;
  }

  /**
   * Called back when one of the {@link Deferred} in the group completes.
   * @param result The result of the deferred.
   */
  private void recordCompletion(final Object result) {
    int size;
    synchronized (this) {
      results.add(result);
      size = results.size();
    }
    if (size == nresults) {
      // From this point on, we no longer need to synchronize in order to
      // access `results' since we know we're done, so no other thread is
      // going to call this method on this instance again.
      for (final Object r : results) {
        if (r instanceof Exception) {
          parent.callback(new DeferredGroupException(results, (Exception) r));
          return;
        }
      }
      parent.callback(results);
    }
  }

  public String toString() {
    return "DeferredGroup"
      + "(parent=" + parent
      + ", # results=" + results.size() + " / " + nresults
      + ')';
  }

}
