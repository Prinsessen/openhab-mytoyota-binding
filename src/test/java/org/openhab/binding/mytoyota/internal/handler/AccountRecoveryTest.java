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
package org.openhab.binding.mytoyota.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mytoyota.internal.api.MyToyotaApiException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.BridgeBuilder;

import com.google.gson.JsonArray;

/**
 * The account must find its own way back after a vehicle reported a failed poll.
 *
 * A vehicle's failed poll takes the account OFFLINE ({@code reportCommunication(false, ...)}), and a vehicle
 * stops polling while its bridge is OFFLINE - so before 1.9.12 nothing ever polled again and the account and
 * car stayed OFFLINE until the thing was restarted by hand (2026-11-03, a FortiGate reboot: 08:13 -> never).
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class AccountRecoveryTest {

    /** The handler with its timer and its cloud call replaced: nothing here reaches Toyota. */
    private static class TestAccountHandler extends MyToyotaAccountHandler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Long> delaysSeconds = new ArrayList<>();
        boolean cloudAnswers = true;
        int cloudCalls;

        TestAccountHandler(Bridge bridge) {
            super(bridge);
        }

        @Override
        @Nullable
        ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            tasks.add(task);
            delaysSeconds.add(unit.toSeconds(delay));
            return null;
        }

        @Override
        public JsonArray listVehicles() throws MyToyotaApiException {
            cloudCalls++;
            if (!cloudAnswers) {
                throw new MyToyotaApiException("HTTP connect timed out");
            }
            return new JsonArray();
        }

        /** Run what is due, as the scheduler would; tasks scheduled meanwhile wait for the next call. */
        void runDue() {
            List<Runnable> due = new ArrayList<>(tasks);
            tasks.clear();
            delaysSeconds.clear();
            due.forEach(Runnable::run);
        }
    }

    private Bridge bridge = BridgeBuilder.create(new ThingTypeUID("mytoyota", "account"), "toyota").build();
    private TestAccountHandler handler = new TestAccountHandler(bridge);

    @BeforeEach
    public void setUp() {
        bridge = BridgeBuilder.create(new ThingTypeUID("mytoyota", "account"), "toyota").build();
        handler = new TestAccountHandler(bridge);
        ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
        // the framework stores the status on the thing; the mock does what ThingManager would
        doAnswer(inv -> {
            bridge.setStatusInfo(inv.getArgument(1));
            return null;
        }).when(callback).statusUpdated(any(), any());
        handler.setCallback(callback);
        bridge.setStatusInfo(new ThingStatusInfo(ThingStatus.ONLINE, ThingStatusDetail.NONE, null));
    }

    @Test
    public void aFailedVehiclePollSchedulesTheAccountsOwnRetry() {
        handler.reportCommunication(false, "HTTP connect timed out");

        assertEquals(ThingStatus.OFFLINE, bridge.getStatus());
        assertEquals(1, handler.tasks.size(), "the account must try again by itself - no vehicle will");
        assertEquals(5 * 60L, handler.delaysSeconds.get(0), "after one poll interval (5 min by default)");
    }

    @Test
    public void theAccountComesBackOnlineWhenTheCloudAnswersAgain() {
        handler.reportCommunication(false, "HTTP connect timed out");
        handler.runDue();

        assertEquals(1, handler.cloudCalls, "the retry asks the cloud, it does not just flip the status");
        assertEquals(ThingStatus.ONLINE, bridge.getStatus());
        assertTrue(handler.tasks.isEmpty(), "nothing more to retry once ONLINE");
    }

    @Test
    public void itKeepsTryingWhileTheCloudIsStillUnreachable() {
        handler.cloudAnswers = false;
        handler.reportCommunication(false, "HTTP connect timed out");
        handler.runDue();

        assertEquals(ThingStatus.OFFLINE, bridge.getStatus());
        assertEquals(1, handler.tasks.size(), "a failed retry schedules the next one");

        handler.cloudAnswers = true;
        handler.runDue();
        assertEquals(ThingStatus.ONLINE, bridge.getStatus());
    }

    @Test
    public void twoFailingVehiclesStartOneRetryNotTwo() {
        handler.reportCommunication(false, "HTTP connect timed out");
        handler.reportCommunication(false, "HTTP connect timed out");

        assertEquals(1, handler.tasks.size());
    }

    @Test
    public void aSuccessfulVehiclePollStillBringsTheAccountBackAtOnce() {
        handler.reportCommunication(false, "HTTP connect timed out");
        handler.reportCommunication(true, null);

        assertEquals(ThingStatus.ONLINE, bridge.getStatus());
    }
}
