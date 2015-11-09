/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.device.communication.mqtt;

import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fusesource.mqtt.client.Future;
import org.fusesource.mqtt.client.FutureConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;

import com.sitewhere.common.MarshalUtils;
import com.sitewhere.device.event.processor.FilteredOutboundEventProcessor;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.device.IDevice;
import com.sitewhere.spi.device.event.IDeviceAlert;
import com.sitewhere.spi.device.event.IDeviceCommandInvocation;
import com.sitewhere.spi.device.event.IDeviceCommandResponse;
import com.sitewhere.spi.device.event.IDeviceEvent;
import com.sitewhere.spi.device.event.IDeviceLocation;
import com.sitewhere.spi.device.event.IDeviceMeasurements;
import com.sitewhere.spi.device.event.processor.IMulticastingOutboundEventProcessor;
import com.sitewhere.spi.device.event.processor.multicast.IDeviceEventMulticaster;
import com.sitewhere.spi.device.event.processor.multicast.IDeviceRouteBuilder;
import com.sitewhere.spi.device.event.processor.multicast.IRoute;

/**
 * Outbound event processor that sends events to an MQTT topic.
 * 
 * @author Derek
 */
public class MqttOutboundEventProcessor extends FilteredOutboundEventProcessor implements
		IMulticastingOutboundEventProcessor<String> {

	/** Static logger instance */
	private static Logger LOGGER = Logger.getLogger(MqttOutboundEventProcessor.class);

	/** Default hostname if not set via Spring */
	public static final String DEFAULT_HOSTNAME = "localhost";

	/** Default port if not set from Spring */
	public static final int DEFAULT_PORT = 1883;

	/** Default connection timeout in seconds */
	public static final long DEFAULT_CONNECT_TIMEOUT_SECS = 5;

	/** Host name */
	private String hostname = DEFAULT_HOSTNAME;

	/** Port */
	private int port = DEFAULT_PORT;

	/** Topic events are posted to */
	private String topic;

	/** MQTT client */
	private MQTT mqtt;

	/** Shared MQTT connection */
	private FutureConnection connection;

	/** Multicaster for events */
	private IDeviceEventMulticaster<String> multicaster;

	/** Route builder for generating topics */
	private IDeviceRouteBuilder<String> routeBuilder = new IDeviceRouteBuilder<String>() {

		@Override
		public IRoute<String> build(final IDevice device) throws SiteWhereException {
			return new IRoute<String>() {

				@Override
				public String getRoute() {
					return "/devices/" + device.getHardwareId();
				}
			};
		}
	};

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#start()
	 */
	@Override
	public void start() throws SiteWhereException {
		if ((topic == null) && (multicaster == null)) {
			throw new SiteWhereException(
					"No topic or multicaster specified for MQTT outbound event processor.");
		}

		// Required for filters.
		super.start();

		// Start the multicaster implementation.
		if (multicaster != null) {
			startNestedComponent(multicaster, true);
		}

		this.mqtt = new MQTT();
		try {
			mqtt.setHost(getHostname(), getPort());
		} catch (URISyntaxException e) {
			throw new SiteWhereException("Invalid hostname for MQTT broker.", e);
		}
		LOGGER.info("Connecting to MQTT broker at '" + getHostname() + ":" + getPort() + "'...");
		connection = mqtt.futureConnection();
		try {
			Future<Void> future = connection.connect();
			future.await(DEFAULT_CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new SiteWhereException("Unable to connect to MQTT broker.", e);
		}
		LOGGER.info("Connected to MQTT broker.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.device.event.processor.FilteredOutboundEventProcessor#stop()
	 */
	@Override
	public void stop() throws SiteWhereException {
		if (connection != null) {
			try {
				connection.disconnect();
				connection.kill();
			} catch (Exception e) {
				LOGGER.error("Error shutting down MQTT device event receiver.", e);
			}
		}
		super.stop();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.device.event.processor.FilteredOutboundEventProcessor#
	 * onMeasurementsNotFiltered(com.sitewhere.spi.device.event.IDeviceMeasurements)
	 */
	@Override
	public void onMeasurementsNotFiltered(IDeviceMeasurements measurements) throws SiteWhereException {
		sendEvent(measurements);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.device.event.processor.FilteredOutboundEventProcessor#
	 * onLocationNotFiltered(com.sitewhere.spi.device.event.IDeviceLocation)
	 */
	@Override
	public void onLocationNotFiltered(IDeviceLocation location) throws SiteWhereException {
		sendEvent(location);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sitewhere.device.event.processor.FilteredOutboundEventProcessor#onAlertNotFiltered
	 * (com.sitewhere.spi.device.event.IDeviceAlert)
	 */
	@Override
	public void onAlertNotFiltered(IDeviceAlert alert) throws SiteWhereException {
		sendEvent(alert);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.device.event.processor.FilteredOutboundEventProcessor#
	 * onCommandInvocationNotFiltered
	 * (com.sitewhere.spi.device.event.IDeviceCommandInvocation)
	 */
	@Override
	public void onCommandInvocationNotFiltered(IDeviceCommandInvocation invocation) throws SiteWhereException {
		sendEvent(invocation);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.device.event.processor.FilteredOutboundEventProcessor#
	 * onCommandResponseNotFiltered(com.sitewhere.spi.device.event.IDeviceCommandResponse)
	 */
	@Override
	public void onCommandResponseNotFiltered(IDeviceCommandResponse response) throws SiteWhereException {
		sendEvent(response);
	}

	/**
	 * Send an {@link IDeviceEvent} to the configured topic.
	 * 
	 * @param event
	 * @throws SiteWhereException
	 */
	protected void sendEvent(IDeviceEvent event) throws SiteWhereException {
		if (getMulticaster() != null) {
			List<String> routes = getMulticaster().calculateRoutes(event, getRouteBuilder());
			for (String route : routes) {
				publish(event, route);
			}
		} else {
			publish(event, getTopic());
		}
	}

	/**
	 * Publish an event to an MQTT topic.
	 * 
	 * @param event
	 * @throws SiteWhereException
	 */
	protected void publish(IDeviceEvent event, String topic) throws SiteWhereException {
		connection.publish(topic, MarshalUtils.marshalJson(event), QoS.AT_LEAST_ONCE, false);
		LOGGER.info("Publishing event " + event.getId() + " to route: " + topic);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#getLogger()
	 */
	@Override
	public Logger getLogger() {
		return LOGGER;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.device.event.processor.IMulticastingOutboundEventProcessor#
	 * getMulticaster()
	 */
	public IDeviceEventMulticaster<String> getMulticaster() {
		return multicaster;
	}

	public void setMulticaster(IDeviceEventMulticaster<String> multicaster) {
		this.multicaster = multicaster;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sitewhere.spi.device.event.processor.IMulticastingOutboundEventProcessor#
	 * getRouteBuilder()
	 */
	public IDeviceRouteBuilder<String> getRouteBuilder() {
		return routeBuilder;
	}

	public void setRouteBuilder(IDeviceRouteBuilder<String> routeBuilder) {
		this.routeBuilder = routeBuilder;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}
}