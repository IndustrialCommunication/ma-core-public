/**
 * Copyright (C) 2014 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.io.serial;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Terry Packer
 *
 */
public abstract class SerialPortInputStream extends InputStream{

	private final Log LOG = LogFactory.getLog(SerialPortInputStream.class);
    private final Object closeLock = new Object();
    private volatile boolean closed = false;

	/* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
    @Override
    public abstract int read() throws IOException;

	/* (non-Javadoc)
	 * @see java.io.InputStream#available()
	 */
	@Override
	public abstract int available() throws IOException;

	/* (non-Javadoc)
	 * @see java.io.InputStream#close()
	 */
	@Override
	public void close() throws IOException {
		if (LOG.isDebugEnabled())
            LOG.debug("Attempting Close of Serial Port Input Stream.");
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closeImpl();
            closed = true;
        }
        if (LOG.isDebugEnabled())
            LOG.debug("Closed Serial Port Input Stream.");
	}
    

	/**
	 * The close implementation for the stream
	 * @throws IOException
	 */
	public abstract void closeImpl() throws IOException;
    

	/**
	 * Peek at the head of the stream, do not remove the byte
	 */
	public abstract int peek();
}
