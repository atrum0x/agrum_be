package com.atrum.agrum.user;

import com.atrum.agrum.user.dto.AppUserDto;
import com.atrum.agrum.user.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<AppUserDto> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AppUserDto>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{username}")
    public ResponseEntity<AppUserDto> findByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.findByUsername(username));
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String username) {
        userService.delete(username);
    }
}
