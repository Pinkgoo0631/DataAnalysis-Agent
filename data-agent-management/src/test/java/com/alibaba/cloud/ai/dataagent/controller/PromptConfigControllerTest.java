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

import com.alibaba.cloud.ai.dataagent.dto.prompt.PromptConfigDTO;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.service.prompt.UserPromptService;
import com.alibaba.cloud.ai.dataagent.service.auth.ResourceOwnershipService;
import com.alibaba.cloud.ai.dataagent.support.AuthenticationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromptConfigControllerTest {

	@Mock
	private UserPromptService promptConfigService;

	@Mock
	private ResourceOwnershipService ownershipService;

	@InjectMocks
	private PromptConfigController controller;

	private UserPromptConfig testConfig;

	private final Authentication authentication = AuthenticationTestSupport.user();

	@BeforeEach
	void setUp() {
		testConfig = new UserPromptConfig();
		when(ownershipService.userId(authentication)).thenReturn(1L);
		when(promptConfigService.getConfigById(anyString(), eq(1L))).thenReturn(testConfig);
	}

	@Test
	void saveConfig_success() {
		PromptConfigDTO dto = new PromptConfigDTO("1", "test", "planner", 1L, "prompt", true, "desc", "admin", 1, 1);
		when(promptConfigService.saveOrUpdateConfig(dto, 1L)).thenReturn(testConfig);

		ResponseEntity<Map<String, Object>> response = controller.saveConfig(dto, authentication);

		assertEquals(200, response.getStatusCode().value());
		assertTrue((Boolean) response.getBody().get("success"));
		assertNotNull(response.getBody().get("data"));
	}

	@Test
	void getConfig_found() {
		when(promptConfigService.getConfigById("1", 1L)).thenReturn(testConfig);

		ResponseEntity<Map<String, Object>> response = controller.getConfig("1", authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertNotNull(response.getBody().get("data"));
	}

	@Test
	void getConfig_notFound() {
		when(promptConfigService.getConfigById("999", 1L)).thenReturn(null);

		ResponseEntity<Map<String, Object>> response = controller.getConfig("999", authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void getAllConfigs_returnsListWithTotal() {
		when(promptConfigService.getAllConfigs(1L)).thenReturn(List.of(testConfig));

		ResponseEntity<Map<String, Object>> response = controller.getAllConfigs(authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertEquals(1, response.getBody().get("total"));
	}

	@Test
	void getConfigsByType_withAgentId() {
		when(promptConfigService.getConfigsByType("planner", 1L, 1L)).thenReturn(List.of(testConfig));

		ResponseEntity<Map<String, Object>> response = controller.getConfigsByType("planner", 1L, authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertEquals(1, response.getBody().get("total"));
	}

	@Test
	void getConfigsByType_withoutAgentId() {
		when(promptConfigService.getConfigsByType("planner", null, 1L)).thenReturn(List.of());

		ResponseEntity<Map<String, Object>> response = controller.getConfigsByType("planner", null, authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertEquals(0, response.getBody().get("total"));
	}

	@Test
	void getActiveConfig_found() {
		when(promptConfigService.getConfigsByType("planner", 1L, 1L)).thenReturn(List.of(testConfig));

		ResponseEntity<Map<String, Object>> response = controller.getActiveConfig("planner", 1L, authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertTrue((Boolean) response.getBody().get("hasCustomConfig"));
	}

	@Test
	void getActiveConfig_notFound() {
		when(promptConfigService.getConfigsByType("planner", null, 1L)).thenReturn(List.of());

		ResponseEntity<Map<String, Object>> response = controller.getActiveConfig("planner", null, authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertFalse((Boolean) response.getBody().get("hasCustomConfig"));
	}

	@Test
	void getActiveConfigs_withResults() {
		testConfig.setEnabled(true);
		when(promptConfigService.getConfigsByType("planner", 1L, 1L)).thenReturn(List.of(testConfig));

		ResponseEntity<Map<String, Object>> response = controller.getActiveConfigs("planner", 1L, authentication);

		assertTrue((Boolean) response.getBody().get("success"));
		assertTrue((Boolean) response.getBody().get("hasOptimizationConfigs"));
		assertEquals(1, response.getBody().get("total"));
	}

	@Test
	void getActiveConfigs_empty() {
		when(promptConfigService.getConfigsByType("planner", 1L, 1L)).thenReturn(List.of());

		ResponseEntity<Map<String, Object>> response = controller.getActiveConfigs("planner", 1L, authentication);

		assertFalse((Boolean) response.getBody().get("hasOptimizationConfigs"));
	}

	@Test
	void deleteConfig_success() {
		when(promptConfigService.deleteConfig("1")).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.deleteConfig("1", authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void deleteConfig_failure() {
		when(promptConfigService.deleteConfig("999")).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.deleteConfig("999", authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void enableConfig_success() {
		when(promptConfigService.enableConfig("1")).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.enableConfig("1", authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void enableConfig_failure() {
		when(promptConfigService.enableConfig("999")).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.enableConfig("999", authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void disableConfig_success() {
		when(promptConfigService.disableConfig("1")).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.disableConfig("1", authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void disableConfig_failure() {
		when(promptConfigService.disableConfig("999")).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.disableConfig("999", authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void getSupportedPromptTypes_returnsTypes() {
		ResponseEntity<Map<String, Object>> response = controller.getSupportedPromptTypes();

		assertTrue((Boolean) response.getBody().get("success"));
		String[] types = (String[]) response.getBody().get("data");
		assertEquals(5, types.length);
	}

	@Test
	void batchEnableConfigs_success() {
		when(promptConfigService.enableConfigs(anyList())).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.batchEnableConfigs(List.of("1", "2"), authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void batchEnableConfigs_failure() {
		when(promptConfigService.enableConfigs(anyList())).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.batchEnableConfigs(List.of("1"), authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void batchDisableConfigs_success() {
		when(promptConfigService.disableConfigs(anyList())).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.batchDisableConfigs(List.of("1", "2"), authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void batchDisableConfigs_failure() {
		when(promptConfigService.disableConfigs(anyList())).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.batchDisableConfigs(List.of("1"), authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void updatePriority_success() {
		when(promptConfigService.updatePriority("1", 5)).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.updatePriority("1", Map.of("priority", 5), authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void updatePriority_failure() {
		when(promptConfigService.updatePriority("999", 5)).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.updatePriority("999", Map.of("priority", 5), authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

	@Test
	void updateDisplayOrder_success() {
		when(promptConfigService.updateDisplayOrder("1", 3)).thenReturn(true);

		ResponseEntity<Map<String, Object>> response = controller.updateDisplayOrder("1", Map.of("displayOrder", 3), authentication);

		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void updateDisplayOrder_failure() {
		when(promptConfigService.updateDisplayOrder("999", 3)).thenReturn(false);

		ResponseEntity<Map<String, Object>> response = controller.updateDisplayOrder("999",
				Map.of("displayOrder", 3), authentication);

		assertFalse((Boolean) response.getBody().get("success"));
	}

}
