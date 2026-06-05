package com.example.demo.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping
    public List<Worker> getAllWorkers() {
        return workerService.getAllWorkers();
    }

    @GetMapping("/{id}")
    public Worker getWorker(@PathVariable Long id) {
        return workerService.getWorker(id);
    }

    @PostMapping
    public ResponseEntity<Worker> createWorker(@Valid @RequestBody Worker worker) {
        Worker created = workerService.createWorker(worker);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public Worker updateWorker(@PathVariable Long id, @Valid @RequestBody Worker updates) {
        return workerService.updateWorker(id, updates);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateWorker(@PathVariable Long id) {
        workerService.deactivateWorker(id);
    }
}
