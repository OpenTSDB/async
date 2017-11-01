package com.stumbleupon.async;

import com.stumbleupon.async.Deferred;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client library which calls get() on High Level Library and expects to be called back with the response for further
 * processing.
 *
 */
public class ClientLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(HighLevelLibrary.class);

    public static void main(String[] args) {
        HighLevelLibrary highLevelLibrary = new HighLevelLibrary();
        LOGGER.info("Calling high level library");
        Deferred<String> deferredOutput = highLevelLibrary.get();
        deferredOutput.addCallback(arg -> {
            LOGGER.info("Got callback after high level task completion: " + arg);
            return arg;
        });
        LOGGER.info("Doing other things in client library");
    }


}
