/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.auth;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticModelMapper;
import com.alibaba.cloud.ai.dataagent.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ResourceOwnershipService {

	private final AgentMapper agentMapper;

	private final DatasourceMapper datasourceMapper;

	private final ChatSessionMapper chatSessionMapper;

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final SemanticModelMapper semanticModelMapper;

	public Long userId(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		return user.id();
	}

	public Agent requireAgent(Long agentId, Authentication authentication) {
		Agent agent = agentMapper.findByIdAndUserId(agentId, userId(authentication));
		if (agent == null) {
			throw notFound();
		}
		return agent;
	}

	public boolean ownsAgent(Long agentId, Authentication authentication) {
		return agentMapper.findByIdAndUserId(agentId, userId(authentication)) != null;
	}

	public Agent requireAgentAccess(Long agentId, Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof Long apiAgentId) {
			if (!apiAgentId.equals(agentId)) {
				throw notFound();
			}
			Agent agent = agentMapper.findById(agentId);
			if (agent == null) throw notFound();
			return agent;
		}
		return requireAgent(agentId, authentication);
	}

	public Datasource requireDatasource(Integer datasourceId, Authentication authentication) {
		Datasource datasource = datasourceMapper.selectByIdAndUserId(datasourceId, userId(authentication));
		if (datasource == null) {
			throw notFound();
		}
		return datasource;
	}

	public ChatSession requireSession(String sessionId, Authentication authentication) {
		ChatSession session = chatSessionMapper.selectBySessionIdAndUserId(sessionId, userId(authentication));
		if (session == null) {
			throw notFound();
		}
		return session;
	}

	public AgentKnowledge requireAgentKnowledge(Integer knowledgeId, Authentication authentication) {
		AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
		if (knowledge == null) {
			throw notFound();
		}
		requireAgent(knowledge.getAgentId().longValue(), authentication);
		return knowledge;
	}

	public BusinessKnowledge requireBusinessKnowledge(Long knowledgeId, Authentication authentication) {
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(knowledgeId);
		if (knowledge == null) {
			throw notFound();
		}
		requireAgent(knowledge.getAgentId(), authentication);
		return knowledge;
	}

	public SemanticModel requireSemanticModel(Long modelId, Authentication authentication) {
		SemanticModel model = semanticModelMapper.selectById(modelId);
		if (model == null) {
			throw notFound();
		}
		requireAgent(model.getAgentId().longValue(), authentication);
		return model;
	}

	private ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "resource not found");
	}

}
