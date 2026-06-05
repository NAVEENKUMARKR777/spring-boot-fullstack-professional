package com.example.demo.site;

import com.example.demo.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;

    public List<Site> getAllSites() {
        return siteRepository.findAllByActiveTrue();
    }

    public Site getSite(Long id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + id));
    }

    @Transactional
    public Site createSite(Site site) {
        return siteRepository.save(site);
    }

    @Transactional
    public Site updateSite(Long id, Site updates) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + id));
        site.setName(updates.getName());
        site.setLocation(updates.getLocation());
        return siteRepository.save(site);
    }

    @Transactional
    public void deactivateSite(Long id) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + id));
        site.setActive(false);
        siteRepository.save(site);
    }
}
