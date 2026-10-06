package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class AmlCbrSchemaRegistrar {

    @Inject
    CbrRecordStore cbrStore;

    void onStart(@Observes StartupEvent ev) {
        cbrStore.registerSchema(AmlCbrSchema.SCHEMA);
    }
}
