package io.casehub.aml.cbr;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmlCbrSchemaRegistrarTest {

    @Test
    void onStart_registersSchema_noError() {
        CbrRecordStore store = new InMemoryCbrRecordStore();
        AmlCbrSchemaRegistrar registrar = new AmlCbrSchemaRegistrar();
        registrar.cbrStore = store;

        assertDoesNotThrow(() -> registrar.onStart(new StartupEvent()));
    }
}
