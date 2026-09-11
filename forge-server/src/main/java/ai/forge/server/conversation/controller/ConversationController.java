package ai.forge.server.conversation.controller;

import ai.forge.server.agent.application.AgentRunSnapshot;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.conversation.application.ConversationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/agent-conversations")
public class ConversationController {
    /* Agent 连续会话应用服务。 */ private final ConversationService service;
    public ConversationController(ConversationService service){this.service=service;}
    @PostMapping public ResponseEntity<ConversationService.Conversation> create(@Valid @RequestBody CreateRequest body,HttpServletRequest request){var result=service.create(user(request),body.title());return ResponseEntity.created(URI.create("/api/v1/agent-conversations/"+result.id())).body(result);}
    @GetMapping public List<ConversationService.Conversation> list(HttpServletRequest request){return service.list(user(request));}
    @GetMapping("/{id}/messages") public List<ConversationService.Message> messages(@PathVariable long id,HttpServletRequest request){return service.messages(user(request),id);}
    @PostMapping("/{id}/messages") public AgentRunSnapshot send(@PathVariable long id,@Valid @RequestBody SendRequest body,HttpServletRequest request){return service.send(user(request),id,body.message(),body.skill(),body.workItemId());}
    private long user(HttpServletRequest request){return AuthController.requireContext(request).userId();}
    public record CreateRequest(/* 会话标题；空值时使用默认标题。 */ @Size(max=255) String title){}
    public record SendRequest(/* 本轮用户指令。 */ @NotBlank @Size(max=10000) String message,/* 本轮 Agent 专业角色。 */ AgentSkill skill,/* 可选工作项上下文。 */ Long workItemId){}
}
