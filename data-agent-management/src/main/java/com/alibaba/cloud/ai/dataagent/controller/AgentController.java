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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.service.agent.AgentService;
import com.alibaba.cloud.ai.dataagent.vo.ApiKeyResponse;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import com.alibaba.cloud.ai.dataagent.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Agent Management Controller */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@AllArgsConstructor
public class AgentController {

	private final AgentService agentService;

	/** Get agent list */
	@GetMapping("/list")
	public List<Agent> list(@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword, Authentication authentication) {
		Long userId = userId(authentication);
		List<Agent> result;
		if (StringUtils.isNotBlank(keyword)) {
			result = agentService.search(keyword, userId);
		}
		else if (StringUtils.isNotBlank(status)) {
			result = agentService.findByStatus(status, userId);
		}
		else {
			result = agentService.findAll(userId);
		}
		return result;
	}

	/** Get agent details by ID */
	@GetMapping("/{id}")
	public Agent get(@PathVariable Long id, Authentication authentication) {
		return checkAgentExists(id, authentication);
	}

	/** Create agent */
	@PostMapping
	public Agent create(@RequestBody Agent agent, Authentication authentication) {
		// Set default status
		if (StringUtils.isBlank(agent.getStatus())) {
			agent.setStatus("draft");
		}
		return agentService.save(agent, userId(authentication));
	}

	/** Update agent */
	@PutMapping("/{id}")
	public Agent update(@PathVariable Long id, @RequestBody Agent agent, Authentication authentication) {
		checkAgentExists(id, authentication);
		agent.setId(id);
		return agentService.save(agent, userId(authentication));
	}

	/** Delete agent */
	@DeleteMapping("/{id}")
	public void delete(@PathVariable Long id, Authentication authentication) {
		checkAgentExists(id, authentication);
		agentService.deleteById(id);
	}

	/** Publish agent */
	@PostMapping("/{id}/publish")
	public Agent publish(@PathVariable Long id, Authentication authentication) {
		Agent agent = checkAgentExists(id, authentication);
		agent.setStatus("published");
		return agentService.save(agent, userId(authentication));
	}

	/** Offline agent */
	@PostMapping("/{id}/offline")
	public Agent offline(@PathVariable Long id, Authentication authentication) {
		Agent agent = checkAgentExists(id, authentication);
		agent.setStatus("offline");
		return agentService.save(agent, userId(authentication));
	}

	/** Get masked API Key status */
	@GetMapping("/{id}/api-key")
	public ApiResponse<ApiKeyResponse> getApiKey(@PathVariable Long id, Authentication authentication) {
		Agent agent = checkAgentExists(id, authentication);
		String masked = agentService.getApiKeyMasked(id);
		return buildApiKeyResponse(masked, agent.getApiKeyEnabled(), "获取 API Key 成功");
	}

	/** Generate API Key */
	@PostMapping("/{id}/api-key/generate")
	public ApiResponse<ApiKeyResponse> generateApiKey(@PathVariable Long id, Authentication authentication) {
		checkAgentExists(id, authentication);
		Agent agent = agentService.generateApiKey(id);
		return buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "生成 API Key 成功");
	}

	/** Reset API Key */
	@PostMapping("/{id}/api-key/reset")
	public ApiResponse<ApiKeyResponse> resetApiKey(@PathVariable Long id, Authentication authentication) {
		checkAgentExists(id, authentication);
		Agent agent = agentService.resetApiKey(id);
		return buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "重置 API Key 成功");
	}

	/** Delete API Key */
	@DeleteMapping("/{id}/api-key")
	public ApiResponse<ApiKeyResponse> deleteApiKey(@PathVariable Long id, Authentication authentication) {
		checkAgentExists(id, authentication);
		Agent agent = agentService.deleteApiKey(id);
		return buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "删除 API Key 成功");
	}

	/** Toggle API Key enable flag */
	@PostMapping("/{id}/api-key/enable")
	public ApiResponse<ApiKeyResponse> toggleApiKey(@PathVariable Long id, @RequestParam("enabled") boolean enabled,
			Authentication authentication) {
		checkAgentExists(id, authentication);
		Agent agent = agentService.toggleApiKey(id, enabled);
		return buildApiKeyResponse(agent.getApiKey() == null ? null : "****", agent.getApiKeyEnabled(),
				"更新 API Key 状态成功");
	}

	private Agent checkAgentExists(Long id, Authentication authentication) {
		Agent agent = agentService.findById(id, userId(authentication));
		if (agent == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agent with id: %d not found".formatted(id));
		}
		return agent;
	}

	private Long userId(Authentication authentication) {
		return ((AuthenticatedUser) authentication.getPrincipal()).id();
	}

	private ApiResponse<ApiKeyResponse> buildApiKeyResponse(String apiKey, Integer apiKeyEnabled, String message) {
		return ApiResponse.success(message, new ApiKeyResponse(apiKey, apiKeyEnabled));
	}

}
