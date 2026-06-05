package com.example.demo.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {

    List<Worker> findAllByActiveTrue();

    Optional<Worker> findByIdAndActiveTrue(Long id);

    boolean existsByPhone(String phone);
}
