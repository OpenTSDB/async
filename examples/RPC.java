package com.stumbleupon.async;

import com.stumbleupon.async.Deferred;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A class which simulates an RPC call
 *
 */
public class RPC {

    private static final Logger LOGGER = LoggerFactory.getLogger(RPC.class);

    /**
     * Simulates an RPC call which takes 5 seconds to finish before returning a String response
     *
     * @return
     */
    public Deferred<String> get() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        Deferred<String> deferred = new Deferred<>();
        Runnable longRunningTask = () -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOGGER.info("RPC call is done");
            deferred.callback("RPC OUTPUT");
        };
        executorService.submit(longRunningTask);
        return deferred;
    }
}
