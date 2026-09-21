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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Anything that stops a call to Toyota Connected Services: login, HTTP, or an error answer.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class MyToyotaApiException extends Exception {

    private static final long serialVersionUID = 1L;

    public MyToyotaApiException(String message) {
        super(message);
    }
}
