/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.infiniteautomation.mango.io.serial.virtual;

import org.apache.commons.lang3.StringUtils;

import com.infiniteautomation.mango.io.serial.SerialPortIdentifier;
import com.infiniteautomation.mango.io.serial.SerialPortProxy;
import com.serotonin.json.spi.JsonProperty;
import com.serotonin.m2m2.i18n.ProcessResult;

/**
 * @author Terry Packer
 *
 */
public class SerialSocketBridgeConfig extends VirtualSerialPortConfig{

	@JsonProperty
	private String address;
	
	@JsonProperty
	private int port;
	
	@JsonProperty
	private int timeout;//in milliseconds
	
	/**
	 * 
	 * @param name
	 * @param address
	 * @param port
	 * @param timeout (ms)
	 */
	public SerialSocketBridgeConfig(String name, String address, int port, int timeout){
		super(name, SerialPortTypes.SERIAL_SOCKET_BRIDGE);
		this.address = address;
		this.port = port;
		this.timeout = timeout;
	}
	
	
	public void validate(ProcessResult response){
		if (timeout < 0)
            response.addContextualMessage("timeout", "validate.cannotBeNegative");
		
		if (port < 0)
            response.addContextualMessage("port", "validate.cannotBeNegative");
		
        if (StringUtils.isBlank(address))
            response.addContextualMessage("address", "validate.required");
	}


	public String getAddress() {
		return address;
	}


	public void setAddress(String address) {
		this.address = address;
	}


	public int getPort() {
		return port;
	}


	public void setPort(int port) {
		this.port = port;
	}


	public int getTimeout() {
		return timeout;
	}


	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}


	/* (non-Javadoc)
	 * @see com.infiniteautomation.mango.io.serial.virtual.VirtualSerialPortConfig#createProxy()
	 */
	@Override
	public SerialPortProxy createProxy(SerialPortIdentifier id) {
		return new SerialSocketBridge(id, address, port, timeout);
	}
	
	
	
	
	
}
