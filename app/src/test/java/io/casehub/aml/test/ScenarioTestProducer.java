package io.casehub.aml.test;

import io.casehub.pages.scenario.runtime.ScenarioConfig;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ScenarioTestProducer {

    @Produces @Mock ScenarioConfig scenarioConfig() { return ScenarioConfig.localhost(); }
}
