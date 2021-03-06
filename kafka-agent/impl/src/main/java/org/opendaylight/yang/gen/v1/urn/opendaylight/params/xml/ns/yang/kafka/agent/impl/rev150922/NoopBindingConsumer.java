/*
 *  Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.impl.rev150922;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ConsumerContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopBindingConsumer implements BindingAwareConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NoopBindingConsumer.class);

    @Override
    public void onSessionInitialized(ConsumerContext session) {
        LOG.info("NoopBindingConsumer initialized");
    }

}
