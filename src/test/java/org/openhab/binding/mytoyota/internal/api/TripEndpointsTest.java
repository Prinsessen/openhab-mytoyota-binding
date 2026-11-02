/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mytoyota.internal.api;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * The trip list no longer takes a route parameter: pytoyoda 6.0.0 dropped it and the cloud has
 * ignored it since about 2026-09-30 - the route comes only from the single-trip endpoint.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class TripEndpointsTest {

    @Test
    public void tripListsAskWithoutARouteParameter() {
        assertFalse(MyToyotaApiClient.ENDPOINT_TRIPS.contains("route="), "the summary list");
        assertFalse(MyToyotaApiClient.ENDPOINT_TRIPS_LIST.contains("route="), "the recent-trips list");
    }

    @Test
    public void tripListsKeepTheirOtherParameters() {
        assertEquals("/v1/trips?from=2026-11-01&to=2026-11-02&summary=true&limit=1&offset=0",
                String.format(MyToyotaApiClient.ENDPOINT_TRIPS, "2026-11-01", "2026-11-02", 1));
        assertEquals("/v1/trips?from=2026-11-01&to=2026-11-02&summary=false&limit=5&offset=0",
                String.format(MyToyotaApiClient.ENDPOINT_TRIPS_LIST, "2026-11-01", "2026-11-02", 5));
    }

    @Test
    public void theSingleTripStillCarriesTheRoute() {
        assertEquals("/v1/trips/abc", String.format(MyToyotaApiClient.ENDPOINT_TRIP_DETAIL, "abc"));
    }
}
