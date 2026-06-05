package com.example.demo.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findAllByActiveTrue();

    Optional<Site> findByIdAndActiveTrue(Long id);
}
