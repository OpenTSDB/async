/*
 * Copyright (c) 2010  StumbleUpon, Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the StumbleUpon nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
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

    final class Notify<T> implements Callback<T, T> {
      public T call(final T arg) {
        recordCompletion(arg);
        return arg;
      }
      public String toString() {
        return "notify DeferredGroup@" + DeferredGroup.super.hashCode();
      }
    };

    final Notify<T> notify = new Notify<T>();

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
