package com.example.demo.worker;

import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public List<Worker> getAllWorkers() {
        return workerRepository.findAllByActiveTrue();
    }

    public Worker getWorker(Long id) {
        return workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + id));
    }

    @Transactional
    public Worker createWorker(Worker worker) {
        if (workerRepository.existsByPhone(worker.getPhone())) {
            throw new BusinessRuleException("DUPLICATE_PHONE",
                    "A worker with phone " + worker.getPhone() + " already exists");
        }
        return workerRepository.save(worker);
    }

    @Transactional
    public Worker updateWorker(Long id, Worker updates) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + id));

        worker.setName(updates.getName());
        worker.setDesignation(updates.getDesignation());
        worker.setDailyWageRate(updates.getDailyWageRate());
        if (updates.getPhone() != null && !updates.getPhone().equals(worker.getPhone())) {
            if (workerRepository.existsByPhone(updates.getPhone())) {
                throw new BusinessRuleException("DUPLICATE_PHONE",
                        "A worker with phone " + updates.getPhone() + " already exists");
            }
            worker.setPhone(updates.getPhone());
        }

        Worker saved = workerRepository.save(worker);

        // Cache invalidation: refresh the active-worker cache entry if worker is clocked in
        refreshActiveWorkerCacheIfPresent(saved);

        return saved;
    }

    @Transactional
    public void deactivateWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + id));
        worker.setActive(false);
        workerRepository.save(worker);
        // Remove from active-workers Redis cache
        evictFromActiveCache(id);
    }

    private void refreshActiveWorkerCacheIfPresent(Worker worker) {
        try {
            String key = "active_worker:" + worker.getId();
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) {
                // Parse current entry, update worker fields, re-write
                var node = objectMapper.readTree(existing);
                var updated = objectMapper.createObjectNode();
                updated.put("workerId", worker.getId());
                updated.put("workerName", worker.getName());
                updated.put("designation", worker.getDesignation().name());
                updated.put("siteId", node.get("siteId").asLong());
                updated.put("siteName", node.get("siteName").asText());
                updated.put("clockInTime", node.get("clockInTime").asText());
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updated));
                log.info("Refreshed active-worker cache for worker {}", worker.getId());
            }
        } catch (Exception e) {
            log.warn("Could not refresh active-worker cache for worker {}: {}", worker.getId(), e.getMessage());
        }
    }

    private void evictFromActiveCache(Long workerId) {
        try {
            redisTemplate.delete("active_worker:" + workerId);
            redisTemplate.opsForSet().remove("active_workers", String.valueOf(workerId));
        } catch (Exception e) {
            log.warn("Could not evict worker {} from active cache: {}", workerId, e.getMessage());
        }
    }
}
