package com.example.demo.site;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    public List<Site> getAllSites() {
        return siteService.getAllSites();
    }

    @GetMapping("/{id}")
    public Site getSite(@PathVariable Long id) {
        return siteService.getSite(id);
    }

    @PostMapping
    public ResponseEntity<Site> createSite(@Valid @RequestBody Site site) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createSite(site));
    }

    @PutMapping("/{id}")
    public Site updateSite(@PathVariable Long id, @Valid @RequestBody Site updates) {
        return siteService.updateSite(id, updates);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateSite(@PathVariable Long id) {
        siteService.deactivateSite(id);
    }
}
