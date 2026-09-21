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
package org.openhab.binding.mytoyota.internal;

import static org.openhab.binding.mytoyota.internal.MyToyotaBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mytoyota.internal.handler.MyToyotaAccountHandler;
import org.openhab.binding.mytoyota.internal.handler.MyToyotaVehicleHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * Creates the account bridge and vehicle handlers.
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.mytoyota", service = ThingHandlerFactory.class)
public class MyToyotaHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_VEHICLE);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID type = thing.getThingTypeUID();
        if (THING_TYPE_ACCOUNT.equals(type)) {
            return new MyToyotaAccountHandler((Bridge) thing);
        }
        if (THING_TYPE_VEHICLE.equals(type)) {
            return new MyToyotaVehicleHandler(thing);
        }
        return null;
    }
}
