package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.impl.rev150922;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.panda.impl.KafkaUserAgentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaModule extends org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.impl.rev150922.AbstractKafkaModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(KafkaModule.class);
    
    public KafkaModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        
        super(identifier, dependencyResolver);
        if (LOG.isDebugEnabled())
        {
            LOG.debug("in KafkaModule()");
        }
    }

    public KafkaModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier, org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.impl.rev150922.KafkaModule oldModule, java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
        if (LOG.isDebugEnabled())
        {
            LOG.debug("in KafkaModule()");
        }
    }

    @Override
    public void customValidation() {
        // add custom validation form module attributes here.
        if (LOG.isDebugEnabled())
        {
            LOG.debug("in customValidation()");
        }
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("in createInstance()");
        }
        final DataBroker dataBroker = getBindingBrokerDependency()
                .registerConsumer(new NoopBindingConsumer())
                .getSALService(DataBroker.class);
         final DOMNotificationService notifyService = getDomBrokerDependency()
                .registerConsumer(new NoopDOMConsumer())
                .getService(DOMNotificationService.class);
        return new KafkaUserAgentFactory(dataBroker, notifyService);
        
        
    }

}
