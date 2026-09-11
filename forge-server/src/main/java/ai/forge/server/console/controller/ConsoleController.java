package ai.forge.server.console.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.console.application.ConsoleModels;
import ai.forge.server.console.application.ConsoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1")
public class ConsoleController {
    /* 控制台公司级查询与看板命令。 */ private final ConsoleService service;
    public ConsoleController(ConsoleService service){this.service=service;}

    @GetMapping("/search") public List<ConsoleModels.SearchResult> search(@RequestParam String q,HttpServletRequest request){return service.search(user(request),q);}
    @GetMapping("/notifications") public List<ConsoleModels.NotificationView> notifications(@RequestParam(defaultValue="30") int limit,HttpServletRequest request){return service.notifications(user(request),limit);}
    @GetMapping("/notifications/unread-count") public Map<String,Long> unread(HttpServletRequest request){return Map.of("count",service.unread(user(request)));}
    @PostMapping("/notifications/{id}/read") public ResponseEntity<Void> read(@PathVariable long id,HttpServletRequest request){service.markRead(user(request),id);return ResponseEntity.noContent().build();}
    @GetMapping("/dashboard/overview") public ConsoleModels.Dashboard dashboard(HttpServletRequest request){return service.dashboard(user(request));}
    @GetMapping("/task-board") public List<ConsoleModels.BoardItem> board(HttpServletRequest request){return service.board(user(request));}
    @PutMapping("/task-board/items/{id}/position") public ResponseEntity<Void> move(@PathVariable long id,@Valid @RequestBody MoveRequest body,HttpServletRequest request){service.move(user(request),id,body.lane(),body.position());return ResponseEntity.noContent().build();}

    private long user(HttpServletRequest request){return AuthController.requireContext(request).userId();}
    public record MoveRequest(/* 目标泳道代码。 */ @NotBlank String lane,/* 目标泳道内位置。 */ @Min(0) long position){}
}
