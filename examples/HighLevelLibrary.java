package com.stumbleupon.async;


import com.stumbleupon.async.Deferred;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High Level library which makes an RPC call and converts the String response from RPC to lower case
 * and returns the result to client library in an async manner.
 */
public class HighLevelLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(HighLevelLibrary.class);

    /**
     * Makes an RPC call. Once RPC completes, expects to be called back with the RPC response.
     * The callback converts the response to lower case and returns it to the client library.
     *
     * @return
     */
    public Deferred<String> get() {
        RPC rpc = new RPC();
        LOGGER.info("Making RPC call");
        Deferred<String> deferredRPCOutput = rpc.get();
        deferredRPCOutput.addCallback(arg -> {
            LOGGER.info("RPC Output: " + arg);
            return arg.toLowerCase();
        });
        LOGGER.info("Doing other things in High Level Library");
        return deferredRPCOutput;
    }
}
