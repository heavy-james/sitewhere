/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.grpc.client.tenant;

import com.sitewhere.grpc.client.ApiDemux;
import com.sitewhere.grpc.client.spi.client.ITenantManagementApiChannel;
import com.sitewhere.grpc.client.spi.client.ITenantManagementApiDemux;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.microservice.IFunctionIdentifier;
import com.sitewhere.spi.microservice.MicroserviceIdentifier;

/**
 * Demultiplexes tenant management requests across one or more API channels.
 * 
 * @author Derek
 *
 * @param <IAssetManagementApiChannel>
 */
public class TenantManagementApiDemux extends ApiDemux<ITenantManagementApiChannel<?>>
	implements ITenantManagementApiDemux {

    public TenantManagementApiDemux(boolean cacheEnabled) {
	super(cacheEnabled);
    }

    /*
     * @see com.sitewhere.grpc.client.spi.IApiDemux#getTargetIdentifier()
     */
    @Override
    public IFunctionIdentifier getTargetIdentifier() {
	return MicroserviceIdentifier.TenantManagement;
    }

    /*
     * @see
     * com.sitewhere.grpc.client.spi.IApiDemux#createApiChannel(java.lang.String,
     * boolean)
     */
    @Override
    public ITenantManagementApiChannel<?> createApiChannel(String host, boolean cacheEnabled)
	    throws SiteWhereException {
	CachedTenantManagementApiChannel.CacheSettings settings = new CachedTenantManagementApiChannel.CacheSettings();
	if (!cacheEnabled) {
	    settings.getTenantConfiguration().setEnabled(false);
	}
	return new CachedTenantManagementApiChannel(this, host, getMicroservice().getInstanceSettings().getGrpcPort(),
		settings);
    }
}