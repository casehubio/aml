UPDATE aml_investigation_summary SET status = 'IN_PROGRESS'
WHERE status IN ('STARTING', 'RUNNING', 'WAITING');

UPDATE aml_investigation_summary SET status = 'FAILED'
WHERE status IN ('FAULTED', 'COMPENSATING', 'COMPENSATED', 'COMPENSATION_FAULTED');
