package io.casehub.aml.ledger;

import java.nio.charset.StandardCharsets;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.jpa.JpaLedgerEntry;

@Entity
@Table(name = "aml_entity_erasure_entry")
@DiscriminatorValue("AML_ENTITY_ERASURE")
public class AmlEntityErasureLedgerEntry extends JpaLedgerEntry {

    @Column(name = "erased_entity_id", nullable = false)
    public String erasedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "erasure_reason", nullable = false)
    public ErasureReason erasureReason;

    @Column(name = "memories_erased", nullable = false)
    public int memoriesErased;

    @Override
    protected byte[] domainContentBytes() {
        final String content = String.join("|",
                erasedEntityId != null ? erasedEntityId : "",
                erasureReason != null ? erasureReason.name() : "",
                String.valueOf(memoriesErased));
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
