package com.company.booking.service;

import com.company.booking.domain.AuditLog;
import com.company.booking.repository.AuditLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Отдельная транзакция: запись журнала сохраняется, даже если основная операция откатилась. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actor, String operation, String entityType, Long entityId, String result, String details) {
        AuditLog l = new AuditLog();
        l.setActor(actor == null ? "system" : actor);
        l.setOperation(operation);
        l.setEntityType(entityType);
        l.setEntityId(entityId);
        l.setResult(result);
        l.setDetails(details != null && details.length() > 500 ? details.substring(0, 500) : details);
        repo.save(l);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> latest() {
        return repo.findTop200ByOrderByOccurredAtDesc();
    }
}
