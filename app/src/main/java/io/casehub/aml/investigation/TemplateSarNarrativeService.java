package io.casehub.aml.investigation;

import io.casehub.aml.domain.SeedNarrative;
import io.casehub.api.spi.routing.IntPreference;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.Preferences;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class TemplateSarNarrativeService implements SarNarrativeService {

    private final PreferenceProvider preferenceProvider;

    @Inject
    public TemplateSarNarrativeService(PreferenceProvider preferenceProvider) {
        this.preferenceProvider = preferenceProvider;
    }

    @Override
    public NarrativeResult draft(NarrativeContext context) {
        int maxSeeds = resolveInt(AmlNarrativePolicyKeys.MAX_SEEDS, 3);
        int maxSeedLength = resolveInt(AmlNarrativePolicyKeys.MAX_SEED_LENGTH, 2000);

        List<SeedNarrative> seeds = context.seeds().stream()
                .limit(maxSeeds)
                .map(s -> s.narrative().length() > maxSeedLength
                        ? new SeedNarrative(s.narrative().substring(0, maxSeedLength),
                                s.similarityScore(), s.flagReason(), s.entityType())
                        : s)
                .toList();

        boolean seeded = !seeds.isEmpty();

        String narrative = seeded
                ? buildSeededNarrative(context, seeds)
                : buildUnseededNarrative(context);

        return new NarrativeResult(narrative, seeded, seeds.size(), AdaptationMethod.DETERMINISTIC);
    }

    private String buildUnseededNarrative(NarrativeContext ctx) {
        var tx = ctx.transaction();
        var sb = new StringBuilder();
        sb.append("SAR narrative for transaction ").append(tx.id()).append(".");
        sb.append(" Amount: ").append(tx.amount()).append(" ").append(tx.currency()).append(".");
        sb.append(" Flag reason: ").append(tx.flagReason()).append(".");
        if (ctx.entity() != null) {
            sb.append(" Entity type: ").append(ctx.entity().entityType()).append(".");
        }
        if (ctx.osint() != null && ctx.osint().declined()) {
            sb.append(" OSINT screening declined.");
        }
        return sb.toString();
    }

    private String buildSeededNarrative(NarrativeContext ctx, List<SeedNarrative> seeds) {
        var tx = ctx.transaction();
        var best = seeds.getFirst();

        var sb = new StringBuilder();
        sb.append("SAR narrative for transaction ").append(tx.id()).append(".");
        sb.append(" Amount: ").append(tx.amount()).append(" ").append(tx.currency()).append(".");
        sb.append(" Flag reason: ").append(tx.flagReason()).append(".");
        if (ctx.entity() != null) {
            sb.append(" Entity type: ").append(ctx.entity().entityType()).append(".");
        }
        if (ctx.osint() != null && ctx.osint().declined()) {
            sb.append(" OSINT screening declined.");
        }
        sb.append(" Adapted from ").append(seeds.size()).append(" similar case(s), highest similarity: ")
          .append(String.format("%.2f", best.similarityScore())).append(".");
        return sb.toString();
    }

    private int resolveInt(io.casehub.platform.api.preferences.PreferenceKey<IntPreference> key, int fallback) {
        try {
            Preferences prefs = preferenceProvider.resolve(
                    io.casehub.platform.api.preferences.SettingsScope.of(
                            io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID,
                            io.casehub.platform.api.path.Path.of("casehubio", "aml", "cbr", "narrative")));
            IntPreference pref = prefs.get(key);
            return pref != null ? pref.value() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
