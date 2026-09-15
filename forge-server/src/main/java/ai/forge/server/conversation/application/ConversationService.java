package ai.forge.server.conversation.application;

import ai.forge.server.agent.application.AgentRunService;
import ai.forge.server.agent.application.AgentRunSnapshot;
import ai.forge.server.agent.domain.AgentSkill;
import ai.forge.server.agent.domain.MediumToolConfirmation;
import ai.forge.server.common.domain.ResourceNotFoundException;
import ai.forge.server.organization.application.OrganizationAccessService;
import ai.forge.server.workitem.application.WorkItemStore;
import ai.forge.server.workitem.domain.WorkItemType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test-unit")
public class ConversationService {
    /* 解析当前公司上下文。 */ private final OrganizationAccessService access;
    /* 持久化会话与可见消息。 */ private final ConversationStore mapper;
    /* 复用权威 Agent Run 创建与调度链路。 */ private final AgentRunService runs;
    /* 根据 Requirement 当前事实确定本轮角色 Skill。 */ private final WorkItemStore workItems;
    public ConversationService(OrganizationAccessService access,ConversationStore mapper,
            AgentRunService runs,WorkItemStore workItems){this.access=access;this.mapper=mapper;
        this.runs=runs;this.workItems=workItems;}

    @Transactional
    public Conversation create(long userId,String title){long organizationId=organization(userId);String value=normalizeTitle(title);mapper.insertConversation(organizationId,userId,value);long id=mapper.lastInsertId();return new Conversation(id,value,null,null,null,Instant.now(),Instant.now(),0);}
    public List<Conversation> list(long userId){long organizationId=organization(userId);return mapper.conversations(organizationId,userId).stream().map(this::conversation).toList();}
    public List<Message> messages(long userId,long id){requireOwner(userId,id);return mapper.messages(id).stream().map(row->new Message(text(row,"sender"),text(row,"body"),nullable(row,"run_id"),instant(row.get("created_at")))).toList();}
    @Transactional
    public AgentRunSnapshot send(long userId,long id,String body,AgentSkill requestedSkill,Long workItemId){long organizationId=requireOwner(userId,id);String message=body==null?"":body.trim();if(message.isEmpty()||message.length()>10000)throw new IllegalArgumentException("message length must be 1..10000");Long bound=mapper.requirementId(organizationId,userId,id);if(bound!=null&&workItemId!=null&&!bound.equals(workItemId))throw new IllegalArgumentException("conversation is already bound to another requirement");Long effectiveWorkItemId=bound==null?workItemId:bound;AgentSkill skill=stageSkill(organizationId,effectiveWorkItemId);if(bound==null&&effectiveWorkItemId!=null){mapper.bindRequirement(organizationId,userId,id,effectiveWorkItemId);Long persisted=mapper.requirementId(organizationId,userId,id);if(!effectiveWorkItemId.equals(persisted))throw new IllegalArgumentException("conversation is already bound to another requirement");}if(requestedSkill!=null&&requestedSkill!=skill)throw new IllegalArgumentException("skill does not match requirement stage");AgentRunSnapshot run=runs.create(userId,organizationId,effectiveWorkItemId,skill,MediumToolConfirmation.ALLOW,message,"conversation-"+id+"-"+UUID.randomUUID(),UUID.randomUUID().toString());mapper.insertUserMessage(id,message,run.id());mapper.touch(id);return run;}
    private AgentSkill stageSkill(long organizationId,Long workItemId){if(workItemId==null)return AgentSkill.PRODUCT;var item=workItems.findByIdAndScope(organizationId,workItemId).orElseThrow(ResourceNotFoundException::new);if(item.type()!=WorkItemType.REQUIREMENT)throw new IllegalArgumentException("conversation context must be a requirement");return StageSkillRouter.route(item.status());}
    private long requireOwner(long userId,long id){long organizationId=organization(userId);if(mapper.owns(organizationId,userId,id)!=1)throw new ResourceNotFoundException();return organizationId;}
    private long organization(long userId){return access.requireContext(userId).organizationId();}
    private String normalizeTitle(String title){String value=title==null?"New conversation":title.trim();if(value.isEmpty())value="New conversation";return value.length()>255?value.substring(0,255):value;}
    private Conversation conversation(Map<String,Object> row){return new Conversation(number(row,"id"),text(row,"title"),nullableNumber(row,"requirement_id"),nullable(row,"requirement_key"),nullable(row,"requirement_title"),instant(row.get("created_at")),instant(row.get("updated_at")),number(row,"version"));}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static String text(Map<String,Object> row,String key){return row.get(key).toString();}
    private static String nullable(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:value.toString();}
    private static Long nullableNumber(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:((Number)value).longValue();}
    private static Instant instant(Object value){return ((LocalDateTime)value).toInstant(ZoneOffset.UTC);}

    public record Conversation(/* 会话标识。 */ long id,/* 会话标题。 */ String title,/* 绑定的 Requirement 标识；未绑定时为空。 */ Long requirementId,/* 绑定 Requirement 的展示编号；未绑定时为空。 */ String requirementKey,/* 绑定 Requirement 的标题；未绑定时为空。 */ String requirementTitle,/* 创建时间。 */ Instant createdAt,/* 最近更新时间。 */ Instant updatedAt,/* 乐观锁版本。 */ long version){}
    public record Message(/* 发送方。 */ String sender,/* 用户可见正文。 */ String body,/* 关联 Run 标识。 */ String runId,/* 创建时间。 */ Instant createdAt){}
}
