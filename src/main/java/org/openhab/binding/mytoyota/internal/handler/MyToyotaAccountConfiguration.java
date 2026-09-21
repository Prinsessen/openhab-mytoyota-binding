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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration of the account bridge.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class MyToyotaAccountConfiguration {
    public String username = "";
    public String password = "";
    /** T = Toyota, L = Lexus, S = Subaru (Europe) */
    public String brand = "T";
    /** Minutes between polls of every vehicle under this account */
    public int pollInterval = 5;
    /**
     * While the car reports that it is charging, ask it for a fresh state of charge before each poll
     * (the realtime-status wake), but not more often than this many minutes. 0 disables the wake.
     */
    public int wakeWhileCharging = 10;
}
