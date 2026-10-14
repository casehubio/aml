package io.casehub.aml.test;

import io.casehub.pages.playbook.runtime.PlaybookConfig;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class PlaybookTestProducer {

    @Produces @Mock PlaybookConfig playbookConfig() { return PlaybookConfig.localhost(); }
}
