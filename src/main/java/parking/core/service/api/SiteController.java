package parking.core.service.api;

import parking.core.service.common.NotFoundException;
import parking.core.service.common.VerificationMode;
import io.github.huongdang0208.accessgate.persistence.AccessSessionRepository;
import parking.core.service.persistence.SiteEntity;
import io.github.huongdang0208.accessgate.persistence.SiteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sites")
public class SiteController {
    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final SiteRepository sites;
    private final AccessSessionRepository sessions;

    public SiteController(SiteRepository sites, AccessSessionRepository sessions) {
        this.sites = sites;
        this.sessions = sessions;
    }

    public record SiteResponse(Long id, String name, VerificationMode verificationMode) {
        static SiteResponse of(SiteEntity s) {
            return new SiteResponse(s.getId(), s.getName(), s.getVerificationMode());
        }
    }

    public record ChangeModeRequest(@NotNull VerificationMode mode) {
    }

    public record OpenSessionResponse(Long sessionId, String plateNumber, String entryGate, Instant enteredAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SiteResponse> list() {
        return sites.findAll().stream().map(SiteResponse::of).toList();
    }

    @GetMapping("/{id}/open-sessions")
    @Transactional(readOnly = true)
    public List<OpenSessionResponse> openSessions(@PathVariable Long id) {
        requireSite(id);
        return sessions.findOpenBySite(id).stream()
                .map(s -> new OpenSessionResponse(s.getId(), s.getVehicle().getPlateNumber(),
                        s.getEntryGate().getCode(), s.getEnteredAt()))
                .toList();
    }

    @PutMapping("/{id}/verification-mode")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SiteResponse changeMode(@PathVariable Long id, @Valid @RequestBody ChangeModeRequest request,
                                   Authentication who) {
        SiteEntity site = requireSite(id);
        VerificationMode previous = site.getVerificationMode();
        site.changeVerificationMode(request.mode());
        audit.info("site={} verificationMode {} -> {} by {}", id, previous, request.mode(), who.getName());
        return SiteResponse.of(site);
    }

    private SiteEntity requireSite(Long id) {
        return sites.findById(id).orElseThrow(() -> new NotFoundException("Site " + id + " not found"));
    }
}
