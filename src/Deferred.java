/*
 * Copyright (c) 2010-2012  The SUAsync Authors.  All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe implementation of a deferred result for easy asynchronous
 * processing.
 * <p>
 * This implementation is based on <a href="http://goo.gl/avIOWO">Twisted's
 * Python {@code Deferred} API</a>.
 *
 * This API is a simple and elegant way of managing asynchronous and dynamic
 * "pipelines" (processing chains) without having to explicitly define a
 * finite state machine.
 *
 * <h1>The tl;dr version</h1>
 *
 * We're all busy and don't always have time to RTFM in details.  Please pay
 * special attention to the <a href="#warranty">invariants</a> you must
 * respect.  Other than that, here's an executive summary of what
 * {@code Deferred} offers:
 * <ul>
 * <li>A {@code Deferred} is like a {@link java.util.concurrent.Future} with
 * a dynamic {@link Callback} chain associated to it.</li>
 * <li>When the deferred result becomes available, the callback chain gets
 * triggered.</li>
 * <li>The result of one callback in the chain is passed on to the next.</li>
 * <li>When a callback returns another {@code Deferred}, the next callback in
 * the chain doesn't get executed until that other {@code Deferred} result
 * becomes available.</li>
 * <li>There are actually two callback chains.  One is for normal processing,
 * the other is for error handling / recovery.  A {@link Callback} that handles
*  errors is called an "errback".</li>
*  <li>{@code Deferred} is an important building block for writing easy-to-use
*  asynchronous APIs in a thread-safe fashion.</li>
 * </ul>
 *
 * <h1>Understanding the concept of {@code Deferred}</h1>
 *
 * The idea is that a {@code Deferred} represents a result that's not yet
 * available.  An asynchronous operation (I/O, RPC, whatever) has been started
 * and will hand its result (be it successful or not) to the {@code Deferred}
 * in the future.  The key difference between a {@code Deferred} and a
 * {@link java.util.concurrent.Future Future} is that a {@code Deferred} has
 * a <strong>callback chain</strong> associated to it, whereas with just a
 * {@code Future} you need get the result manually at some point, which poses
 * problems such as: How do you know when the result is available?  What if
 * the result itself depends on another future?
 * <p>
 * When you start an asynchronous operation, you typically want to be called
 * back when the operation completes.  If the operation was successful, you
 * want your callback to use its result to carry on what you were doing at
 * the time you started the asynchronous operation.  If there was an error,
 * you want to trigger some error handling code.
 * <p>
 * But there's more to a {@code Deferred} than a single callback.  You can add
 * arbitrary number of callbacks, which effectively allows you to easily build
 * complex processing pipelines in a really simple and elegant way.
 *
 * <h2>Understanding the callback chain</h2>
 * Let's take a typical example.  You're writing a client library for others
 * to use your simple remote storage service.  When your users call the {@code
 * get} method in your library, you want to retrieve some piece of data from
 * your remote service and hand it back to the user, but you want to do so in
 * an asynchronous fashion.
 * <p>
 * When the user of your client library invokes {@code get}, you assemble a
 * request and send it out to the remote server through a socket.  Before
 * sending it to the socket, you create a {@code Deferred} and you store it
 * somewhere, for example in a map, to keep an association between the request
 * and this {@code Deferred}.  You then return this {@code Deferred} to the
 * user, this is how they will access the deferred result as soon as the RPC
 * completes.
 * <p>
 * Sooner or later, the RPC will complete (successfully or not), and your
 * socket will become readable (or maybe closed, in the event of a failure).
 * Let's assume for now that everything works as expected, and thus the socket
 * is readable, so you read the response from the socket.  At this point you
 * extract the result of the remote {@code get} call, and you hand it out to
 * the {@code Deferred} you created for this request (remember, you had to
 * store it somewhere, so you could give it the deferred result once you have
 * it).  The {@code Deferred} then stores this result and triggers any
 * callback that may have been added to it.  The expectation is that the user
 * of your client library, after calling your {@code get} method, will add a
 * {@link Callback} to the {@code Deferred} you gave them.  This way, when the
 * deferred result becomes available, you'll call it with the result in
 * argument.
 * <p>
 * So far what we've explained is nothing more than a {@code Future} with a
 * callback associated to it.  But there's more to {@code Deferred} than just
 * this.  Let's assume now that someone else wants to build a caching layer on
 * top of your client library, to avoid repeatedly {@code get}ting the same
 * value over and over again through the network.  Users who want to use the
 * cache will invoke {@code get} on the caching library instead of directly
 * calling your client library.
 * <p>
 * Let's assume that the caching library already has a result cached for a
 * {@code get} call.  It will create a {@code Deferred}, and immediately hand
 * it the cached result, and it will return this {@code Deferred} to the user.
 * The user will add a {@link Callback} to it, which will be immediately
 * invoked since the deferred result is already available.  So the entire
 * {@code get} call completed virtually instantaneously and entirely from the
 * same thread.  There was no context switch (no other thread involved, no
 * I/O and whatnot), nothing ever blocked, everything just happened really
 * quickly.
 * <p>
 * Now let's assume that the caching library has a cache miss and needs to do
 * a remote {@code get} call using the original client library described
 * earlier.  The RPC is sent out to the remote server and the client library
 * returns a {@code Deferred} to the caching library.  This is where things
 * become exciting.  The caching library can then add its own callback to the
 * {@code Deferred} before returning it to the user.  This callback will take
 * the result that came back from the remote server, add it to the cache and
 * return it.  As usual, the user then adds their own callback to process the
 * result.  So now the {@code Deferred} has 2 callbacks associated to it:
 * <pre>
 *              1st callback       2nd callback
 *
 *   Deferred:  add to cache  -->  user callback
 * </pre>
 * When the RPC completes, the original client library will de-serialize the
 * result from the wire and hand it out to the {@code Deferred}.  The first
 * callback will be invoked, which will add the result to the cache of the
 * caching library.  Then whatever the first callback returns will be passed
 * on to the second callback.  It turns out that the caching callback returns
 * the {@code get} response unchanged, so that will be passed on to the user
 * callback.
 * <p>
 * Now it's very important to understand that the first callback could have
 * returned another arbitrary value, and that's what would have been passed
 * to the second callback.  This may sound weird at first but it's actually
 * the key behind {@code Deferred}.
 * <p>
 * To illustrate why, let's complicate things a bit more.  Let's assume the
 * remote service that serves those {@code get} requests is a fairly simple
 * and low-level storage service (think {@code memcached}), so it only works
 * with byte arrays, it doesn't care what the contents is.  So the original
 * client library is only de-serializing the byte array from the network and
 * handing that byte array to the {@code Deferred}.
 * <p>
 * Now you're writing a higher-level library that uses this storage system
 * to store some of your custom objects.  So when you get the byte array from
 * the server, you need to further de-serialize it into some kind of an object.
 * Users of your higher-level library don't care about what kind of remote
 * storage system you use, the only thing they care about is {@code get}ting
 * those objects asynchronously.  Your higher-level library is built on top
 * of the original low-level library that does the RPC communication.
 * <p>
 * When the users of the higher-level library call {@code get}, you call
 * {@code get} on the lower-level library, which issues an RPC call and
 * returns a {@code Deferred} to the higher-level library.  The higher-level
 * library then adds a first callback to further de-serialize the byte array
 * into an object.  Then the user of the higher-level library adds their own
 * callback that does something with that object.  So now we have something
 * that looks like this:
 * <pre>
 *              1st callback                    2nd callback
 *
 *   Deferred:  de-serialize to an object  -->  user callback
 * </pre>
 * <p>
 * When the result comes in from the network, the byte array is de-serialized
 * from the socket.  The first callback is invoked and its argument is the
 * <strong>initial result</strong>, the byte array.  So the first callback
 * further de-serializes it into some object that it returns.  The second
 * callback is then invoked and its argument is <strong>the result of the
 * previous callback</strong>, that is the de-serialized object.
 * <p>
 * Now back to the caching library, which has nothing to do with the higher
 * level library.  All it does is, given an object that implements some
 * interface with a {@code get} method, it keeps a map of whatever arguments
 * {@code get} receives to an {@code Object} that was cached for this
 * particular {@code get} call.  Thanks to the way the callback chain works,
 * it's possible to use the caching library together with the higher-level
 * library transparently.  Users who want to use caching simply need to
 * use the caching library together with the higher level library.  Now when
 * they call {@code get} on the caching library, and there's a cache miss,
 * here's what happens, step by step:
 * <ol>
 *   <li>The caching library calls {@code get} on the higher-level
 *       library.</li>
 *   <li>The higher-level library calls {@code get} on the lower-level
 *       library.</li>
 *   <li>The lower-level library creates a {@code Deferred}, issues out the
 *       RPC call and returns its {@code Deferred}.</li>
 *   <li>The higher-level library adds its own object de-serialization
 *       callback to the {@code Deferred} and returns it.</li>
 *   <li>The caching library adds its own cache-updating callback to the
 *       {@code Deferred} and returns it.</li>
 *   <li>The user gets the {@code Deferred} and adds their own callback
 *       to do something with the object retrieved from the data store.</li>
 * </ol>
 * <pre>
 *              1st callback       2nd callback       3rd callback
 *
 *   Deferred:  de-serialize  -->  add to cache  -->  user callback
 *   result: (none available)
 * </pre>
 * Once the response comes back, the first callback is invoked, it
 * de-serializes the object, returns it.  The <em>current result</em> of the
 * {@code Deferred} becomes the de-serialized object.  The current state of
 * the {@code Deferred} is as follows:
 * <pre>
 *              2nd callback       3rd callback
 *
 *   Deferred:  add to cache  -->  user callback
 *   result: de-serialized object
 * </pre>
 * Because there are more callbacks in the chain, the {@code Deferred} invokes
 * the next one and gives it the current result (the de-serialized object) in
 * argument.  The callback adds that object to its cache and returns it
 * unchanged.
 * <pre>
 *              3rd callback
 *
 *   Deferred:  user callback
 *   result: de-serialized object
 * </pre>
 * Finally, the user's callback is invoked with the object in argument.
 * <pre>
 *   Deferred:  (no more callbacks)
 *   result: (whatever the user's callback returned)
 * </pre>
 * If you think this is becoming interesting, read on, you haven't reached the
 * most interesting thing about {@code Deferred} yet.
 *
 * <h2>Building dynamic processing pipelines with {@code Deferred}</h2>
 * Let's complicate the previous example a little bit more.  Let's assume that
 * the remote storage service that serves those {@code get} calls is a
 * distributed service that runs on many machines.  The data is partitioned
 * over many nodes and moves around as nodes come and go (due to machine
 * failures and whatnot).  In order to execute a {@code get} call, the
 * low-level client library first needs to know which server is currently
 * serving that piece of data.  Let's assume that there's another server,
 * which is part of that distributed service, that maintains an index and
 * keeps track of where each piece of data is.  The low-level client library
 * first needs to lookup the location of the data using that first server
 * (that's a first RPC), then retrieves it from the storage node (that's
 * another RPC).  End users don't care that retrieving data involves a 2-step
 * process, they just want to call {@code get} and be called back when the
 * data (a byte array) is available.
 * <p>
 * This is where what's probably the most useful feature of {@code Deferred}
 * comes in.  When the user calls {@code get}, the low-level library will issue
 * a first RPC to the index server to locate the piece of data requested by the
 * user.  When issuing this {@code lookup} RPC, a {@code Deferred} gets
 * created.  The low-level {@code get} code adds a first callback to process
 * the {@code lookup} response and then returns it to the user.
 * <pre>
 *              1st callback       2nd callback
 *
 *   Deferred:  index lookup  -->  user callback
 *   result: (none available)
 * </pre>
 * Eventually, the {@code lookup} RPC completes, and the {@code Deferred} is
 * given the {@code lookup} response.  So before triggering the first
 * callback, the {@code Deferred} will be in this state:
 * <pre>
 *              1st callback       2nd callback
 *
 *   Deferred:  index lookup  -->  user callback
 *   result: lookup response
 * </pre>
 * The first callback runs and now knows where to find the piece of data
 * initially requested.  It issues the {@code get} request to the right storage
 * node.  Doing so creates <em>another</em> {@code Deferred}, let's call it
 * {@code (B)}, which is then returned by the {@code index lookup} callback.
 * And this is where the magic happens.  Now we're in this state:
 * <pre>
 *   (A)        2nd callback    |   (B)
 *                              |
 *   Deferred:  user callback   |   Deferred:  (no more callbacks)
 *   result: Deferred (B)       |   result: (none available)
 * </pre>
 * Because a callback returned a {@code Deferred}, we can't invoke the user
 * callback just yet, since the user doesn't want their callback receive a
 * {@code Deferred}, they want it to receive a byte array.  The current
 * callback gets <em>paused</em> and stops processing the callback chain.
 * This callback chain needs to be resumed whenever the {@code Deferred} of
 * the {@code get} call [{@code (B)}] completes.  In order to achieve that, a
 * callback is added to that other {@code Deferred} that will <em>resume</em>
 * the execution of the callback chain.
 * <pre>
 *   (A)        2nd callback    |   (B)        1st callback
 *                              |
 *   Deferred:  user callback   |   Deferred:  resume (A)
 *   result: Deferred (B)       |   result: (none available)
 * </pre>
 * Once {@code (A)} added the callback on {@code (B)}, it can return
 * immediately, there's no need to wait, block a thread or anything like that.
 * So the whole process of receiving the {@code lookup} response and sending
 * out the {@code get} RPC happened really quickly, without blocking anything.
 * <p>
 * Now when the {@code get} response comes back from the network, the RPC
 * layer de-serializes the byte array, as usual, and hands it to {@code (B)}:
 * <pre>
 *   (A)        2nd callback    |   (B)        1st callback
 *                              |
 *   Deferred:  user callback   |   Deferred:  resume (A)
 *   result: Deferred (B)       |   result: byte array
 * </pre>
 * {@code (B)}'s first and only callback is going to set the result of
 * {@code (A)} and resume {@code (A)}'s callback chain.
 * <pre>
 *   (A)        2nd callback    |   (B)        1st callback
 *                              |
 *   Deferred:  user callback   |   Deferred:  resume (A)
 *   result: byte array         |   result: byte array
 * </pre>
 * So now {@code (A)} resumes its callback chain, and invokes the user's
 * callback with the byte array in argument, which is what they wanted.
 * <pre>
 *   (A)                        |   (B)        1st callback
 *                              |
 *   Deferred:  (no more cb)    |   Deferred:  resume (A)
 *   result: (return value of   |   result: byte array
 *            the user's cb)
 * </pre>
 * Then {@code (B)} moves on to its next callback in the chain, but there are
 * none, so {@code (B)} is done too.
 * <pre>
 *   (A)                        |   (B)
 *                              |
 *   Deferred:  (no more cb)    |   Deferred:  (no more cb)
 *   result: (return value of   |   result: byte array
 *            the user's cb)
 * </pre>
 * The whole process of reading the {@code get} response, resuming the initial
 * {@code Deferred} and executing the second {@code Deferred} happened all in
 * the same thread, sequentially, and without blocking anything (provided that
 * the user's callback didn't block, as it must not).
 * <p>
 * What we've done is essentially equivalent to dynamically building an
 * implicit finite state machine to handle the life cycle of the {@code get}
 * request.  This simple API allows you to build arbitrarily complex
 * processing pipelines that make dynamic decisions at each stage of the
 * pipeline as to what to do next.
 *
 * <h2>Handling errors</h2>
 * A {@code Deferred} has in fact not one but two callback chains.  The first
 * chain is the "normal" processing chain, and the second is the error
 * handling chain.  Twisted calls an error handling callback an "errback", so
 * we've kept that term here.  When the asynchronous processing completes with
 * an error, the {@code Deferred} must be given the {@link Exception} that was
 * caught instead of giving it the result (or if no {@link Exception} was
 * caught, one must be created and handed to the {@code Deferred}).  When the
 * current result of a {@code Deferred} is an instance of {@link Exception},
 * the next errback is invoked.  As for normal callbacks, whatever the errback
 * returns becomes the current result.  If the current result is still an
 * instance of {@link Exception}, the next errback is invoked.  If the current
 * result is no longer an {@link Exception}, the next callback is invoked.
 * <p>
 * When a callback or an errback itself <em>throws</em> an exception, it is
 * caught by the {@code Deferred} and becomes the current result, which means
 * that the next errback in the chain will be invoked with that exception in
 * argument.  Note that {@code Deferred} will only catch {@link Exception}s,
 * not any {@link Throwable} or {@link Error}.
 *
 * <a name="warranty"></a>
 * <h1>Contract and Invariants</h1>
 *
 * Read this carefully as this is your warranty.
 * <ul>
 *   <li>A {@code Deferred} can receive only one initial result.</li>
 *   <li>Only one thread at a time is going to execute the callback chain.</li>
 *   <li>Each action taken by a callback
 *       <a href="http://goo.gl/361hAV">happens-before</a> the next
 *       callback is invoked.  In other words, if a callback chain manipulates
 *       a variable (and no one else manipulates it), no synchronization is
 *       required.</li>
 *   <li>The thread that executes the callback chain is the thread that hands
 *       the initial result to the {@code Deferred}.  This class does not
 *       create or manage any thread or executor.</li>
 *   <li>As soon as a callback is executed, the {@code Deferred} will lose its
 *       reference to it.</li>
 *   <li>Every method that adds a callback to a {@code Deferred} does so in
 *       {@code O(1)}.</li>
 *   <li>A {@code Deferred} cannot receive itself as an initial or
 *       intermediate result, as this would cause an infinite recursion.</li>
 *   <li>You must not build a cycle of mutually dependant {@code Deferred}s,
 *       as this would cause an infinite recursion (thankfully, it will
 *       quickly fail with a {@link CallbackOverflowError}).</li>
 *   <li>Callbacks and errbacks cannot receive a {@code Deferred} in
 *       argument.  This is because they always receive the result of a
 *       previous callback, and when the result becomes a {@code Deferred},
 *       we suspend the execution of the callback chain until the result of
 *       that other {@code Deferred} is available.</li>
 *   <li>Callbacks cannot receive an {@link Exception} in argument.  This
 *       because they're always given to the errbacks.</li>
 *   <li>Using the monitor of a {@code Deferred} can lead to a deadlock, so
 *       don't use it.  In other words, writing
 *       <pre>synchronized (some_deferred) { ... }</pre>
 *       (or anything equivalent) voids your warranty.</li>
 * </ul>
 * @param <T> The type of the deferred result.
 */
public final class Deferred<T> {

  // I apologize in advance for all the @SuppressWarnings("unchecked") in this
  // class.  I feel like I'm cheating the type system but, to be honest, Java's
  // type erasure makes all this type checking mostly worthless anyway.  It's
  // really the first time (for me) where type erasure comes in handy though,
  // as it makes it easy to "adjust" <T> on a Deferred as Callbacks are added.

  private static final Logger LOG = LoggerFactory.getLogger(Deferred.class);

  /**
   * Maximum length of the callback chain we allow.
   * Set this to some arbitrary limit to quickly detect problems in code
   * that creates potentially infinite chains.  I can't imagine a practical
   * case that would require a chain with more callbacks than this.
   */
  private static final short MAX_CALLBACK_CHAIN_LENGTH = (1 << 14) - 1;
  // NOTE: The current implementation cannot support more than this many
  // callbacks because indexes used to access the `callbacks' array are
  // of type `short' to save memory.

  /**
   * How many entries do we create in the callback+errback chain by default.
   * Because a callback is always accompanied by a corresponding errback, this
   * value must be even and must be greater than or equal to 2.  Based on the
   * observation that most of the time, chains have on average 2 callbacks,
   * pre-allocating 4 entries means that no reallocation will occur.
   */
  private static final byte INIT_CALLBACK_CHAIN_SIZE = 4;

  /**
   * The state of this {@link Deferred}.
   * <p>
   * The FSM (Finite State Machine) is as follows:
   * <pre>
   *                ,---------------------,
   *                |   ,-------,         |
   *                V   v       |         |
   *   PENDING --> RUNNING --> DONE     PAUSED
   *                  |                   ^
   *                  `-------------------'
   * </pre>
   * A Deferred starts in the PENDING state, unless it's given a result when
   * it's instantiated in which case it starts in the DONE state.
   * When {@link #callback()} is invoked, the Deferred enters the RUNNING
   * state and starts to execute its callback chain.
   * Once the callback chain is exhausted, the Deferred enters the state DONE.
   *
   * While we're executing callbacks, if one of them returns another Deferred,
   * then we enter the state PAUSED, and we add a callback on the other
   * Deferred so we can resume execution (and return to the RUNNING state)
   * once that other Deferred is DONE.
   *
   * When we're DONE, if additional callbacks are added, we're going to
   * execute them immediately (since we already have the deferred result
   * available), which means we're going to return to the RUNNING state.
   */
  private static final byte PENDING = 0;
  private static final byte RUNNING = 1;
  private static final byte PAUSED = 2;
  private static final byte DONE = 3;

  /**
   * The current state of this Deferred.
   * All state transitions must be done atomically.  Since this attribute is
   * volatile, a single assignment is atomic.  But if you need to do a state
   * transition with a test first ("if state is A, move to state B") then you
   * must use {@link #casState} to do an atomic CAS (Compare And Swap).
   */
  private volatile int state;
  // Technically   ^^^ this could be a byte, but unfortunately !@#$% Java only
  // supports CAS on 3 things: references, long, and int.  Even through Unsafe.

  /**
   * The current result.  This reference is either of type T or Exception.
   *
   * This reference doesn't need to be volatile because it's guaranteed that
   * only 1 thread at a time will access this reference, and all accesses
   * happen in between a volatile access on the {@link #state} variable.
   *
   * This reference isn't touched until the initial call to {@link #callback}
   * occurs.  The thread that invokes {@link #callback} is going to run through
   * the callback chain in {@link #runCallbacks}, but it will first set the
   * state to RUNNING (volatile access).  When the method returns, the state
   * is changed either to DONE or PAUSED (volatile access).  When going out of
   * PAUSED, the state is set back to RUNNING before updating the result
   * (volatile access).  Going from state DONE to RUNNING again also involves
   * a volatile access.
   *
   * The new Java memory model thus guarantees that accesses to this reference
   * will always be consistent, since they always happen after a volatile
   * access to the state associated with this reference.  See also the FAQ on
   * <a href="http://goo.gl/q14k27">JSR 133 (Java Memory Model)</a>.
   */
  private Object result;

  /**
   * The current callback and errback chains (can be null).
   * Invariants:
   *   - Let i = 2 * n.  The ith entry is a callback, and (i+1)th is the
   *     corresponding errback.  In other words, even entries are callbacks
   *     and odd entries are errbacks of the callback right before them.
   *   - When not null, the length is between {@link #INIT_CALLBACK_CHAIN_SIZE}
   *     and {@link #MAX_CALLBACK_CHAIN_LENGTH} * 2 (both limits inclusive).
   *   - This array is only grown, never shrunk.
   *   - If state is PENDING, this list may  be null.
   *   - If state is DONE,    this list must be null.
   *   - All accesses to this list must be done while synchronizing on `this'.
   * Technically, this array could be used as a circular buffer to save RAM.
   * However because the typical life of a Deferred is to accumulate callbacks
   * and then run through them all in one shot, this would mostly lead to more
   * complicated code with little practical gains.  Circular buffer would help
   * when item removals and insertions are interspersed, but this is uncommon.
   * @see #next_callback
   * @see #last_callback
   */
  private Callback[] callbacks;

  /**
   * Index in {@link #callbacks} of the next callback to invoke.
   * Invariants:
   *   - When entering DONE, this value is reset to 0.
   *   - All the callbacks prior to this index are null.
   *   - If `callbacks' isn't null, the callback at this index is not null
   *     unless {@code next_callback == last_callback}.
   *   - All accesses to this value must be done while synchronizing on `this'.
   */
  private short next_callback;

  /**
   * Index in {@link #callbacks} past the last callback to invoke.
   * Invariants:
   *   - When entering DONE, this value is reset to 0.
   *   - All the callbacks at and after this index are null.
   *   - This value might be equal to {@code callbacks.length}.
   *   - All accesses to this value must be done while synchronizing on `this'.
   */
  private short last_callback;

  /** Helper for atomic CAS on the state.  */
  private static final AtomicIntegerFieldUpdater<Deferred> stateUpdater =
    AtomicIntegerFieldUpdater.newUpdater(Deferred.class, "state");

  /**
   * Atomically compares and swaps the state of this Deferred.
   * @param cmp The expected state to compare against.
   * @param val The new state to transition to if the comparison is successful.
   * @return {@code true} if the CAS succeeded, {@code false} otherwise.
   */
  private boolean casState(final int cmp, final int val) {
    return stateUpdater.compareAndSet(this, cmp, val);
  }

  /** Constructor.  */
  public Deferred() {
    state = PENDING;
  }

  /** Private constructor used when the result is already available.  */
  private Deferred(final Object result) {
    this.result = result;
    state = DONE;
  }

  /**
   * Constructs a {@code Deferred} with a result that's readily available.
   * <p>
   * This is equivalent to writing:
   * <pre>{@literal
   *   Deferred<T> d = new Deferred<T>();
   *   d.callback(result);
   * }</pre>
   * Callbacks added to this {@code Deferred} will be immediately called.
   * @param result The "deferred" result.
   * @return a new {@code Deferred}.
   */
  public static <T> Deferred<T> fromResult(final T result) {
    return new Deferred<T>(result);
  }

  /**
   * Constructs a {@code Deferred} with an error that's readily available.
   * <p>
   * This is equivalent to writing:
   * <pre>{@literal
   *   Deferred<T> d = new Deferred<T>();
   *   d.callback(error);
   * }</pre>
   * Errbacks added to this {@code Deferred} will be immediately called.
   * @param error The error to use as a result.
   */
  public static <T> Deferred<T> fromError(final Exception error) {
    return new Deferred<T>(error);
  }

  /**
   * Registers a callback and an "errback".
   * <p>
   * If the deferred result is already available, the callback or the errback
   * (depending on the nature of the result) is executed immediately from this
   * thread.
   * @param cb The callback to register.
   * @param eb Th errback to register.
   * @return {@code this} with an "updated" type.
   * @throws CallbackOverflowError if there are too many callbacks in this chain.
   * The maximum number of callbacks allowed in a chain is set by the
   * implementation.  The limit is high enough that you shouldn't have to worry
   * about this exception (which is why it's an {@link Error} actually).  If
   * you hit it, you probably did something wrong.
   */
  @SuppressWarnings("unchecked")
  public <R, R2, E> Deferred<R> addCallbacks(final Callback<R, T> cb,
                                             final Callback<R2, E> eb) {
    if (cb == null) {
      throw new NullPointerException("null callback");
    } else if (eb == null) {
      throw new NullPointerException("null errback");
    }
    // We need to synchronize on `this' first before the CAS, to prevent
    // runCallbacks from switching our state from RUNNING to DONE right
    // before we add another callback.
    synchronized (this) {
      // If we're DONE, switch to RUNNING atomically.
      if (state == DONE) {
        // This "check-then-act" sequence is safe as this is the only code
        // path that transitions from DONE to RUNNING and it's synchronized.
        state = RUNNING;
      } else {
        // We get here if weren't DONE (most common code path)
        //  -or-
        // if we were DONE and another thread raced with us to change the
        // state and we lost the race (uncommon).
        if (callbacks == null) {
          callbacks = new Callback[INIT_CALLBACK_CHAIN_SIZE];
        }
        // Do we need to grow the array?
        else if (last_callback == callbacks.length) {
          final int oldlen = callbacks.length;
          if (oldlen == MAX_CALLBACK_CHAIN_LENGTH * 2) {
            throw new CallbackOverflowError("Too many callbacks in " + this
              + " (size=" + (oldlen / 2) + ") when attempting to add cb="
              + cb + '@' + cb.hashCode() + ", eb=" + eb + '@' + eb.hashCode());
          }
          final int len = Math.min(oldlen * 2, MAX_CALLBACK_CHAIN_LENGTH * 2);
          final Callback[] newcbs = new Callback[len];
          System.arraycopy(callbacks, next_callback,  // Outstanding callbacks.
                           newcbs, 0,            // Move them to the beginning.
                           last_callback - next_callback);  // Number of items.
          last_callback -= next_callback;
          next_callback = 0;
          callbacks = newcbs;
        }
        callbacks[last_callback++] = cb;
        callbacks[last_callback++] = eb;
        return (Deferred<R>) ((Deferred) this);
      }
    }  // end of synchronized block

    if (!doCall(result instanceof Exception ? eb : cb)) {
      // While we were executing the callback, another thread could have
      // added more callbacks.  If doCall returned true, it means we're
      // PAUSED, so we won't reach this point, because the Deferred we're
      // waiting on will call us back later.  But if we're still in state
      // RUNNING, we'll get to here, and we must check to see if any new
      // callbacks were added while we were executing doCall, because if
      // there are, we must execute them immediately, because no one else
      // is going to execute them for us otherwise.
      boolean more;
      synchronized (this) {
        more = callbacks != null && next_callback != last_callback;
      }
      if (more) {
        runCallbacks();  // Will put us back either in DONE or in PAUSED.
      } else {
        state = DONE;
      }
    }
    return (Deferred<R>) ((Object) this);
  }

  /**
   * Registers a callback.
   * <p>
   * If the deferred result is already available and isn't an exception, the
   * callback is executed immediately from this thread.
   * If the deferred result is already available and is an exception, the
   * callback is discarded.
   * If the deferred result is not available, this callback is queued and will
   * be invoked from whichever thread gives this deferred its initial result
   * by calling {@link #callback}.
   * @param cb The callback to register.
   * @return {@code this} with an "updated" type.
   */
  public <R> Deferred<R> addCallback(final Callback<R, T> cb) {
    return addCallbacks(cb, Callback.PASSTHROUGH);
  }

  /**
   * Registers a callback.
   * <p>
   * This has the exact same effect as {@link #addCallback}, but keeps the type
   * information "correct" when the callback to add returns a {@code Deferred}.
   * @param cb The callback to register.
   * @return {@code this} with an "updated" type.
   */
  @SuppressWarnings("unchecked")
  public <R, D extends Deferred<R>>
    Deferred<R> addCallbackDeferring(final Callback<D, T> cb) {
    return addCallbacks((Callback<R, T>) ((Object) cb), Callback.PASSTHROUGH);
  }

  /**
   * Registers an "errback".
   * <p>
   * If the deferred result is already available and is an exception, the
   * errback is executed immediately from this thread.
   * If the deferred result is already available and isn't an exception, the
   * errback is discarded.
   * If the deferred result is not available, this errback is queued and will
   * be invoked from whichever thread gives this deferred its initial result
   * by calling {@link #callback}.
   * @param eb The errback to register.
   * @return {@code this} with an "updated" type.
   */
  @SuppressWarnings("unchecked")
  public <R, E> Deferred<T> addErrback(final Callback<R, E> eb) {
    return addCallbacks((Callback<T, T>) ((Object) Callback.PASSTHROUGH), eb);
  }

  /**
   * Registers a callback both as a callback and as an "errback".
   * <p>
   * If the deferred result is already available, the callback is executed
   * immediately from this thread (regardless of whether or not the current
   * result is an exception).
   * If the deferred result is not available, this callback is queued and will
   * be invoked from whichever thread gives this deferred its initial result
   * by calling {@link #callback}.
   * @param cb The callback to register.
   * @return {@code this} with an "updated" type.
   */
  public <R> Deferred<R> addBoth(final Callback<R, T> cb) {
    return addCallbacks(cb, cb);
  }

  /**
   * Registers a callback both as a callback and as an "errback".
   * <p>
   * This has the exact same effect as {@link #addBoth}, but keeps the type
   * information "correct" when the callback to add returns a {@code Deferred}.
   * @param cb The callback to register.
   * @return {@code this} with an "updated" type.
   */
  @SuppressWarnings("unchecked")
  public <R, D extends Deferred<R>>
    Deferred<R> addBothDeferring(final Callback<D, T> cb) {
    return addCallbacks((Callback<R, T>) ((Object) cb),
                        (Callback<R, T>) ((Object) cb));
  }

  /**
   * Chains another {@code Deferred} to this one.
   * <p>
   * This method simply ensures that whenever the callback chain in
   * {@code this} is run, then the callback chain in {@code other}
   * gets run too.  The result handed to {@code other} is whatever
   * result {@code this} currently has.
   * <p>
   * One case where this is particularly useful is when you want to multiplex
   * a result to multiple {@code Deferred}s, that is if multiple "listeners"
   * want to have their callback chain triggered when a single event completes:
   * <pre>{@literal
   * public class ResultMultiplexer {
   *   private Deferred<Foo> listeners = new Deferred<Foo>();
   *
   *   public void addListener(Deferred<Foo> d) {
   *     listeners.chain(d);
   *   }
   *
   *   public void emitResult(Foo event) {
   *     listeners.callback(event);
   *     // Remember that a Deferred is a one-time callback chain.
   *     // Once emitResult is called, everyone interested in getting the
   *     // next event would need to call addListener again.  This isn't a
   *     // pub-sub system, it's just showing how to multiplex a result.
   *     listeners = new Deferred<Foo>();
   *   }
   * }
   * }</pre>
   * @param other The {@code Deferred} to chain to this one.
   * @return {@code this}, always.
  * @throws AssertionError if {@code this == other}.
   */
  public Deferred<T> chain(final Deferred<T> other) {
    if (this == other) {
      throw new AssertionError("A Deferred cannot be chained to itself."
                               + "  this=" + this);
    }
    final Chain<T> cb = new Chain<T>(other);
    return addCallbacks(cb, cb);
  }

  /**
   * Callback used to chain a deferred with another one.
   */
  private static final class Chain<T> implements Callback<T, T> {
    private final Deferred<T> other;

    /**
     * Constructor.
     * @param other The other deferred to chain with.
     */
    public Chain(final Deferred<T> other) {
      this.other = other;
    }

    public T call(final T arg) {
      other.callback(arg);
      return arg;
    }

    public String toString() {
      return "chain with Deferred@" + other.hashCode();
    }
  };

  /**
   * Groups multiple {@code Deferred}s together in a single one.
   * <p>
   * Conceptually, this does the opposite of {@link #chain}, in the sense that
   * it demultiplexes multiple {@code Deferred}s into a single one, so that you
   * can easily take an action once all the {@code Deferred}s in the group have
   * been called back.
   * <pre>{@literal
   * public class ResultDemultiplexer {
   *   private ArrayList<Deferred<Foo>> deferreds = new ArrayList<Deferred<Foo>>();
   *
   *   public void addDeferred(final Deferred<Foo> d) {
   *     deferreds.add(d);
   *   }
   *
   *   public Deferred<ArrayList<Object>> demultiplex() {
   *     Deferred<ArrayList<Object>> demultiplexed = Deferred.group(deferreds);
   *     deferreds.clear();
   *     return demultiplexed;
   *   }
   * }
   * }</pre>
   * In the example above, any number of {@code Deferred} can be added to
   * the demultiplexer, and once {@code demultiplex()} is invoked, the
   * {@code Deferred} returned will be invoked once all the {@code Deferred}s
   * added to the demultiplexer have been called back.
   * @param deferreds All the {@code Deferred}s to group together.
   * @return A new {@code Deferred} that will be called back once all the
   * {@code Deferred}s given in argument have been called back.  Each element
   * in the list will be either of type {@code <T>} or an {@link Exception}.
   * If any of the elements in the list is an {@link Exception},
   * the errback of the {@code Deferred} returned will be invoked
   * with a {@link DeferredGroupException} in argument.
   * <p>
   * There's no guarantee on the order of the results in the deferred list
   * returned, it depends on the order in which the {@code Deferred}s in the
   * group complete.  If you want to preserve the order, use
   * {@link #groupInOrder(Collection)} instead.
   */
  public static <T>
    Deferred<ArrayList<T>> group(final Collection<Deferred<T>> deferreds) {
    return new DeferredGroup<T>(deferreds, false).getDeferred();
  }

  /**
   * Groups multiple {@code Deferred}s together in a single one.
   * <p>
   * This is the same thing as {@link #group(Collection)} except that we
   * guarantee we preserve the order of the {@code Deferred}s.
   * @param deferreds All the {@code Deferred}s to group together.
   * @return A new {@code Deferred} that will be called back once all the
   * {@code Deferred}s given in argument have been called back.
   * @see #group(Collection)
   * @since 1.4
   */
  public static <T>
    Deferred<ArrayList<T>> groupInOrder(final Collection<Deferred<T>> deferreds) {
    return new DeferredGroup<T>(deferreds, true).getDeferred();
  }

  /**
   * Groups two {@code Deferred}s together in a single one.
   * <p>
   * This is semantically equivalent to:
   * <pre>{@link #group(Collection) group}({@link java.util.Arrays#asList
   * Arrays.asList}(d1, d2));</pre>except that it's type safe as it doesn't
   * involve an unchecked generic array creation of type {@code Deferred<T>}
   * for the varargs parameter passed to {@code asList}.
   * @param d1 The first {@code Deferred} to put in the group.
   * @param d2 The second {@code Deferred} to put in the group.
   * @return A new {@code Deferred} that will be called back once both
   * {@code Deferred}s given in argument have been called back.
   * @see #group(Collection)
   */
  public static <T>
    Deferred<ArrayList<T>> group(final Deferred<T> d1, final Deferred<T> d2) {
    final ArrayList<Deferred<T>> tmp = new ArrayList<Deferred<T>>(2);
    tmp.add(d1);
    tmp.add(d2);
    return new DeferredGroup<T>(tmp, false).getDeferred();
  }

  /**
   * Groups three {@code Deferred}s together in a single one.
   * <p>
   * This is semantically equivalent to:
   * <pre>{@link #group(Collection) group}({@link java.util.Arrays#asList
   * Arrays.asList}(d1, d2, d3));</pre>except that it's type safe as it doesn't
   * involve an unchecked generic array creation of type {@code Deferred<T>}
   * for the varargs parameter passed to {@code asList}.
   * @param d1 The first {@code Deferred} to put in the group.
   * @param d2 The second {@code Deferred} to put in the group.
   * @param d3 The third {@code Deferred} to put in the group.
   * @return A new {@code Deferred} that will be called back once all three
   * {@code Deferred}s given in argument have been called back.
   * @see #group(Collection)
   */
  public static <T>
    Deferred<ArrayList<T>> group(final Deferred<T> d1,
                                 final Deferred<T> d2,
                                 final Deferred<T> d3) {
    final ArrayList<Deferred<T>> tmp = new ArrayList<Deferred<T>>(3);
    tmp.add(d1);
    tmp.add(d2);
    tmp.add(d3);
    return new DeferredGroup<T>(tmp, false).getDeferred();
  }

  /**
   * Starts running the callback chain.
   * <p>
   * This posts the initial result that will be passed to the first callback
   * in the callback chain.  If the argument is an {@link Exception} then
   * the "errback" chain will be triggered instead.
   * <p>
   * This method will not let any {@link Exception} thrown by a callback
   * propagate.  You shouldn't try to catch any {@link RuntimeException} when
   * you call this method, as this is unnecessary.
   * @param initresult The initial result with which to start the 1st callback.
   * The following must be true:
   * {@code initresult instanceof T || initresult instanceof }{@link Exception}
   * @throws AssertionError if this method was already called on this instance.
   * @throws AssertionError if {@code initresult == this}.
   */
  public void callback(final Object initresult) {
    if (!casState(PENDING, RUNNING)) {
      throw new AssertionError("This Deferred was already called!"
        + "  New result=" + initresult + ", this=" + this);
    }
    result = initresult;
    if (initresult instanceof Deferred) {
      // Twisted doesn't allow a callback chain to start with another Deferred
      // but I don't see any reason.  Maybe it was to help prevent people from
      // creating recursive callback chains that would never terminate?  We
      // already check for the obvious in handleContinuation by preventing
      // this Deferred from depending on itself, but there's no way to prevent
      // people from building mutually dependant Deferreds or complex cyclic
      // chains of Deferreds, unless we keep track in a set of all the
      // Deferreds we go through while executing a callback chain, which seems
      // like an unnecessary complication for uncommon cases (bad code). Plus,
      // when that actually happens and people write buggy code that creates
      // cyclic chains, they will quickly get a CallbackOverflowError.
      final Deferred d = (Deferred) initresult;
      if (this == d) {
        throw new AssertionError("A Deferred cannot be given to itself"
                                 + " as a result.  this=" + this);
      }
      handleContinuation(d, null);
    }
    runCallbacks();
  }

  /**
   * Synchronously waits until this Deferred is called back.
   * <p>
   * This helps do synchronous operations using an asynchronous API.
   * If this Deferred already completed, this method returns (or throws)
   * immediately.  Otherwise, the current thread will be <em>blocked</em>
   * and will wait until the Deferred is called back.
   * @return The deferred result, at this point in the callback chain.
   * @throws InterruptedException if this thread was interrupted before the
   * deferred result became available.
   * @throws Exception if the deferred result is an exception, this exception
   * will be thrown.
   */
  public T join() throws InterruptedException, Exception {
    return doJoin(true, 0);
  }

  /**
   * Synchronously waits until this Deferred is called back or a timeout occurs.
   * <p>
   * This helps do synchronous operations using an asynchronous API.
   * If this Deferred already completed, this method returns (or throws)
   * immediately.  Otherwise, the current thread will be <em>blocked</em>
   * and will wait until the Deferred is called back or the specified amount
   * of time has elapsed.
   * @param timeout The maximum time to wait in milliseconds.  A value of 0
   * means no timeout.
   * @return The deferred result, at this point in the callback chain.
   * @throws InterruptedException if this thread was interrupted before the
   * deferred result became available.
   * @throws IllegalArgumentException If the value of timeout is negative.
   * @throws TimeoutException if there's a timeout.
   * @throws Exception if the deferred result is an exception, this exception
   * will be thrown.
   * @since 1.1
   */
  public T join(final long timeout) throws InterruptedException, Exception {
    return doJoin(true, timeout);
  }

  /**
   * Synchronously waits until this Deferred is called back.
   * <p>
   * This helps do synchronous operations using an asynchronous API.
   * If this Deferred already completed, this method returns (or throws)
   * immediately.  Otherwise, the current thread will be <em>blocked</em>
   * and will wait until the Deferred is called back.  If the current thread
   * gets interrupted while waiting, it will keep waiting anyway until the
   * callback chain terminates, before returning (or throwing an exception)
   * the interrupted status on the thread will be set again.
   * @return The deferred result, at this point in the callback chain.
   * @throws Exception if the deferred result is an exception, this exception
   * will be thrown.
   */
  public T joinUninterruptibly() throws Exception {
    try {
      return doJoin(false, 0);
    } catch (InterruptedException e) {
      throw new AssertionError("Impossible");
    }
  }

  /**
   * Synchronously waits until this Deferred is called back or a timeout occurs.
   * <p>
   * This helps do synchronous operations using an asynchronous API.
   * If this Deferred already completed, this method returns (or throws)
   * immediately.  Otherwise, the current thread will be <em>blocked</em>
   * and will wait until the Deferred is called back or the specified amount
   * of time has elapsed.  If the current thread gets interrupted while
   * waiting, it will keep waiting anyway until the callback chain terminates,
   * before returning (or throwing an exception) the interrupted status on the
   * thread will be set again.
   * @param timeout The maximum time to wait in milliseconds.  A value of 0
   * means no timeout.
   * @return The deferred result, at this point in the callback chain.
   * @throws IllegalArgumentException If the value of timeout is negative.
   * @throws TimeoutException if there's a timeout.
   * @throws Exception if the deferred result is an exception, this exception
   * will be thrown.
   * @since 1.1
   */
  public T joinUninterruptibly(final long timeout) throws Exception {
    try {
      return doJoin(false, timeout);
    } catch (InterruptedException e) {
      throw new AssertionError("Impossible");
    }
  }

  /**
   * Synchronously waits until this Deferred is called back.
   * @param interruptible Whether or not to let {@link InterruptedException}
   * interrupt us.  If {@code false} then this method will never throw
   * {@link InterruptedException}, it will handle it and keep waiting.  In
   * this case though, the interrupted status on this thread will be set when
   * this method returns.
   * @param timeout The maximum time to wait in milliseconds.  A value of 0
   * means no timeout.
   * @return The deferred result, at this point in the callback chain.
   * @throws IllegalArgumentException If the value of timeout is negative.
   * @throws InterruptedException if {@code interruptible} is {@code true} and
   * this thread was interrupted while waiting.
   * @throws Exception if the deferred result is an exception, this exception
   * will be thrown.
   * @throws TimeoutException if there's a timeout.
   */
  @SuppressWarnings("unchecked")
  private T doJoin(final boolean interruptible, final long timeout)
  throws InterruptedException, Exception {
    if (state == DONE) {  // Nothing to join, we're already DONE.
      if (result instanceof Exception) {
        throw (Exception) result;
      }
      return (T) result;
    }

    final Signal signal_cb = new Signal();

    // Dealing with InterruptedException properly is a PITA.  I highly
    // recommend reading http://goo.gl/aeOOXT to understand how this works.
    boolean interrupted = false;
    try {
      while (true) {
        try {
          boolean timedout = false;
          synchronized (signal_cb) {
            addBoth((Callback<T, T>) ((Object) signal_cb));
            if (timeout == 0) {  // No timeout, we can use a simple loop.
              // If we get called back immediately, we won't enter the loop.
              while (signal_cb.result == signal_cb) {
                signal_cb.wait();
              }
            } else if (timeout < 0) {
              throw new IllegalArgumentException("negative timeout: " + timeout);
            } else {  // We have a timeout, the loop is a bit more complicated.
              long timeleft = timeout * 1000000L;  // Convert to nanoseconds.
              if (timeout > 31556926000L) {  // One year in milliseconds.
                // Most likely a programming bug.
                LOG.warn("Timeout (" + timeout + ") is long than 1 year."
                         + "  this=" + this);
                if (timeleft <= 0) {  // Very unlikely.
                  throw new IllegalArgumentException("timeout overflow after"
                    + " conversion to nanoseconds: " + timeout);
                }
              }
              // If we get called back immediately, we won't enter the loop.
              while (signal_cb.result == signal_cb) {
                // We can't distinguish between a timeout and a spurious wakeup.
                // So we have to time how long we slept to figure out whether we
                // timed out or how long we need to sleep again.  There's no
                // better way to do this, that's how it's implemented in the JDK
                // in `AbstractQueuedSynchronizer.ConditionObject.awaitNanos()'.
                long duration = System.nanoTime();
                final long millis = timeleft / 1000000L;
                final int nanos = (int) (timeleft % 1000000);
                // This API is annoying because it won't let us specify just
                // nanoseconds.  The second argument must be less than 1000000.
                signal_cb.wait(millis, nanos);
                duration = System.nanoTime() - duration;
                timeleft -= duration;
                // If we have 0ns or less left, the timeout has expired
                // already.  If we have less than 100ns left, there's no
                // point in looping again, as just going through the loop
                // above easily takes 100ns on a modern x86-64 CPU, after
                // JIT compilation.  `nanoTime()' is fairly cheap since it's
                // not even a system call, it just reads a hardware counter.
                // But entering `wait' is pretty much guaranteed to make the
                // loop take more than 100ns no matter what.
                if (timeleft < 100) {
                  timedout = true;
                  break;
                }
              }
            }
          }
          if (timedout && signal_cb.result == signal_cb) {
            // Give up if we timed out *and* we haven't gotten a result yet.
            throw new TimeoutException(this, timeout);
          } else if (signal_cb.result instanceof Exception) {
            throw (Exception) signal_cb.result;
          }
          return (T) signal_cb.result;
        } catch (InterruptedException e) {
          LOG.debug("While joining {}: interrupted", this);
          interrupted = true;
          if (interruptible) {
            throw e;  // Let the exception propagate out.
          }
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();  // Restore the interrupted status.
      }
    }
  }

  /**
   * Callback to wake us up when this Deferred is running.
   * We will wait() on the intrinsic condition of this callback.
   */
  static final class Signal implements Callback<Object, Object> {
    // When the callback triggers, it'll replace the reference in this
    // array to something other than `this' -- since the result cannot
    // possibly be `this'.
    Object result = this;

    private final String thread = Thread.currentThread().getName();

    public Object call(final Object arg) {
      synchronized (this) {
        result = arg;
        super.notify();  // Guaranteed to have only 1 thread wait()ing.
      }
      return arg;
    }

    public String toString() {
      return "wakeup thread " + thread;
    }
  };

  /**
   * Executes all the callbacks in the current chain.
   */
  private void runCallbacks() {
    while (true) {
      Callback cb = null;
      Callback eb = null;
      synchronized (this) {
        // Read those into local variables so we can call doCall and invoke the
        // callbacks without holding the lock on `this', which would cause a
        // deadlock if we try to addCallbacks to `this' while a callback is
        // running.
        if (callbacks != null && next_callback != last_callback) {
          cb = callbacks[next_callback++];
          eb = callbacks[next_callback++];
        }
        // Also, we may need to atomically change the state to DONE.
        // Otherwise if another thread is blocked in addCallbacks right before
        // we're done processing the last element, we'd enter state DONE and
        // leave this method, and then addCallbacks would add callbacks that
        // would never get called.
        else {
          state = DONE;
          callbacks = null;
          next_callback = last_callback = 0;
          break;
        }
      }

      //final long start = System.nanoTime();
      //LOG.debug("START >>>>>>>>>>>>>>>>>> doCall(" + cb + ", " + eb + ')');
      if (doCall(result instanceof Exception ? eb : cb)) {
        //LOG.debug("PAUSE ================== doCall(" + cb + ", " + eb
        //          + ") in " + (System.nanoTime() - start) / 1000 + "us");
        break;
      }
      //LOG.debug("DONE  <<<<<<<<<<<<<<<<<< doCall(" + cb + ", " + eb
      //          + "), result=" + result
      //          + " in " + (System.nanoTime() - start) / 1000 + "us");
    }
  }

  /**
   * Executes a single callback, handling continuation if it returns a Deferred.
   * @param cb The callback to execute.
   * @return {@code true} if the callback returned a Deferred and we switched to
   * PAUSED, {@code false} otherwise and we didn't change state.
   */
  @SuppressWarnings("unchecked")
  private boolean doCall(final Callback cb) {
    try {
      //LOG.debug("doCall(" + cb + '@' + cb.hashCode() + ')' + super.hashCode());
      result = cb.call(result);
    } catch (Exception e) {
      result = e;
    }

    if (result instanceof Deferred) {
      handleContinuation((Deferred) result, cb);
      return true;
    }
    return false;
  }

  /**
   * Chains this Deferred with another one we need to wait on.
   * @param d The other Deferred we need to wait on.
   * @param cb The callback that returned that Deferred or {@code null} if we
   * don't know where this Deferred comes from (it was our initial result).
   * @throws AssertionError if {@code this == d} as this would cause an
   * infinite recursion.
   */
  @SuppressWarnings("unchecked")
  private void handleContinuation(final Deferred d, final Callback cb) {
    if (this == d) {
      final String cb2s = cb == null ? "null" : cb + "@" + cb.hashCode();
      throw new AssertionError("After " + this + " executed callback=" + cb2s
        + ", the result returned was the same Deferred object.  This is illegal"
        + ", a Deferred can't run itself recursively.  Something is wrong.");
    }
    // Optimization: if `d' is already DONE, instead of calling
    // adding a callback on `d' to continue when `d' completes,
    // we atomically read the result off of `d' immediately.  To
    // do this safely, we need to change `d's state momentarily.
    if (d.casState(DONE, RUNNING)) {
      result = d.result;  // No one will change `d.result' now.
      d.state = DONE;
      runCallbacks();
      return;
    }

    // Our `state' was either RUNNING or DONE.
    // If it was RUNNING, we have to suspend the callback chain here and
    // resume when the result of that other Deferred is available.
    // If it was DONE, well we're no longer DONE because we now need to wait
    // on that other Deferred to complete, so we're PAUSED again.
    state = PAUSED;
    d.addBoth(new Continue(d, cb));
    // If d is DONE and our callback chain is empty, we're now in state DONE.
    // Otherwise we're still in state PAUSED.
    if (LOG.isDebugEnabled() && state == PAUSED) {
      if (cb != null) {
        LOG.debug("callback=" + cb + '@' + cb.hashCode() + " returned " + d
                  + ", so the following Deferred is getting paused: " + this);
      } else {
        LOG.debug("The following Deferred is getting paused: " + this
                  + " as it received another Deferred as a result: " + d);
      }
    }
  }

  /**
   * A {@link Callback} to resume execution after another Deferred.
   */
  private final class Continue implements Callback<Object, Object> {
    private final Deferred d;
    private final Callback cb;

    /**
     * Constructor.
     * @param d The other Deferred we need to resume after.
     * @param cb The callback that returned that Deferred or {@code null} if we
     * don't know where this Deferred comes from (it was our initial result).
     */
    public Continue(final Deferred d, final Callback cb) {
      this.d = d;
      this.cb = cb;
    }

    public Object call(final Object arg) {
      if (arg instanceof Deferred) {
        handleContinuation((Deferred) arg, cb);
      } else if (!casState(PAUSED, RUNNING)) {
        final String cb2s = cb == null ? "null" : cb + "@" + cb.hashCode();
        throw new AssertionError("Tried to resume the execution of "
          + Deferred.this + ") although it's not in state=PAUSED."
          + "  This occurred after the completion of " + d
          + " which was originally returned by callback=" + cb2s);
      }
      result = arg;
      runCallbacks();
      return arg;
    }

    public String toString() {
      return "(continuation of Deferred@" + Deferred.super.hashCode()
        + " after " + (cb != null ? cb + "@" + cb.hashCode() : d) + ')';
    }
  };

  /**
   * Returns a helpful string representation of this {@code Deferred}.
   * <p>
   * The string returned is built in {@code O(N)} where {@code N} is the
   * number of callbacks currently in the chain.  The string isn't built
   * entirely atomically, so it can appear to show this {@code Deferred}
   * in a slightly inconsistent state.
   * <p>
   * <b>This method is not cheap</b>.  Avoid doing:
   * <pre>{@literal
   * Deferred<Foo> d = ..;
   * LOG.debug("Got " + d);
   * }</pre>
   * The overhead of stringifying the {@code Deferred} can be significant,
   * especially if this is in the fast-path of your application.
   */
  public String toString() {
    final int state = this.state;  // volatile access before reading result.
    final Object result = this.result;
    final String str;
    if (result == null) {
      str = "null";
    } else if (result instanceof Deferred) {  // Nested Deferreds
      str = "Deferred@" + result.hashCode();  // are hard to read.
    } else {
      str = result.toString();
    }

    // We can't easily estimate how much space we'll need for the callback
    // chains, so let's just make sure we have enough space for all the static
    // cruft and the result, and double that to avoid the first re-allocation.
    // If result is a very long string, we may end up wasting some space, but
    // it's not likely to happen and even less likely to be a problem.
    final StringBuilder buf = new StringBuilder((9 + 10 + 7 + 7
                                                 + str.length()) * 2);
    buf.append("Deferred@").append(super.hashCode())
      .append("(state=").append(stateString(state))
      .append(", result=").append(str)
      .append(", callback=");
    synchronized (this) {
      if (callbacks == null || next_callback == last_callback) {
        buf.append("<none>, errback=<none>");
      } else {
        for (int i = next_callback; i < last_callback; i += 2) {
          buf.append(callbacks[i]).append(" -> ");
        }
        buf.setLength(buf.length() - 4);  // Remove the extra " -> ".
        buf.append(", errback=");
        for (int i = next_callback + 1; i < last_callback; i += 2) {
          buf.append(callbacks[i]).append(" -> ");
        }
        buf.setLength(buf.length() - 4);  // Remove the extra " -> ".
      }
    }
    buf.append(')');
    return buf.toString();
  }

  private static String stateString(final int state) {
    switch (state) {
      case PENDING: return "PENDING";
      case RUNNING: return "RUNNING";
      case PAUSED:  return "PAUSED";
      case DONE:    return "DONE";
    }
    throw new AssertionError("Should never be here.  WTF: state=" + state);
  }

}
