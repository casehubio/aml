package io.casehub.aml.test;

import io.casehub.blocks.agentic.social.InnerLifeConfig;
import io.casehub.blocks.agentic.social.MentalModelConfig;
import io.casehub.blocks.agentic.social.MoodConfig;
import io.casehub.blocks.agentic.social.PersonalityEvolutionConfig;
import io.casehub.blocks.agentic.social.StrategyLearningConfig;
import io.casehub.blocks.agentic.social.UserModelConfig;
import io.casehub.blocks.agentic.social.drive.DriveConfig;
import io.casehub.blocks.agentic.social.emergence.NormDetectionConfig;
import io.casehub.blocks.agentic.social.goal.GoalEscalationConfig;
import io.casehub.blocks.agentic.social.goal.GoalProposalConfig;
import io.casehub.blocks.agentic.social.narrative.NarrativeConfig;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class BlocksSocialTestProducer {

    @Produces @Mock MoodConfig moodConfig() { return MoodConfig.defaults(); }
    @Produces @Mock MentalModelConfig mentalModelConfig() { return MentalModelConfig.defaults(); }
    @Produces @Mock InnerLifeConfig innerLifeConfig() { return InnerLifeConfig.defaults(); }
    @Produces @Mock PersonalityEvolutionConfig personalityEvolutionConfig() { return PersonalityEvolutionConfig.defaults(); }
    @Produces @Mock StrategyLearningConfig strategyLearningConfig() { return StrategyLearningConfig.defaults(); }
    @Produces @Mock UserModelConfig userModelConfig() { return UserModelConfig.defaults(); }
    @Produces @Mock DriveConfig driveConfig() { return DriveConfig.defaults(); }
    @Produces @Mock NormDetectionConfig normDetectionConfig() { return NormDetectionConfig.defaults(); }
    @Produces @Mock GoalEscalationConfig goalEscalationConfig() { return GoalEscalationConfig.defaults(); }
    @Produces @Mock GoalProposalConfig goalProposalConfig() { return GoalProposalConfig.defaults(); }
    @Produces @Mock NarrativeConfig narrativeConfig() { return NarrativeConfig.defaults(); }
}
