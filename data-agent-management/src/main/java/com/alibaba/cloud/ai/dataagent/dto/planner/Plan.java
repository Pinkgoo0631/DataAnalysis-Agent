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
package com.alibaba.cloud.ai.dataagent.dto.planner;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

	@JsonProperty("thought_process")
	@JsonPropertyDescription("简要描述你的分析思路。必须明确提到你检查了哪些表和字段")
	private String thoughtProcess;

	@JsonProperty("execution_plan")
	@JsonPropertyDescription("执行计划的步骤列表")
	private List<ExecutionStep> executionPlan;

	@Override
	public String toString() {
		return "Plan{" + "thoughtProcess='" + thoughtProcess + '\'' + ", executionPlan=" + executionPlan + '}';
	}

	/**
	 * 自动生成计划未通过校验时使用的兜底计划：直接执行一次 SQL 查询并生成报告，
	 * 保证流程始终有可输出内容，避免静默结束。
	 */
	public static String fallbackPlan(String canonicalQuery) {
		String instruction = (canonicalQuery == null || canonicalQuery.isBlank()) ? "查询并分析数据" : canonicalQuery;
		ExecutionStep sqlStep = new ExecutionStep();
		ExecutionStep.ToolParameters sqlParameters = new ExecutionStep.ToolParameters();
		sqlParameters.setInstruction(instruction);
		sqlStep.setStep(1);
		sqlStep.setToolToUse(Constant.SQL_GENERATE_NODE);
		sqlStep.setToolParameters(sqlParameters);

		ExecutionStep reportStep = new ExecutionStep();
		ExecutionStep.ToolParameters reportParameters = new ExecutionStep.ToolParameters();
		reportParameters.setSummaryAndRecommendations(
				"仅根据步骤1的SQL查询真实结果总结分析结论；若结果为空，如实说明未查询到数据，不臆测原因。");
		reportStep.setStep(2);
		reportStep.setToolToUse(Constant.REPORT_GENERATOR_NODE);
		reportStep.setToolParameters(reportParameters);

		Plan plan = new Plan("自动生成计划未通过校验，退化为直接SQL查询并输出报告，以保证结果可用。",
				List.of(sqlStep, reportStep));
		try {
			return JsonUtil.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(plan);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize fallback plan", e);
		}
	}

	// 为NL2SQL模式准备的Plan，只走SQL生成，并将实际问题作为步骤指令传给下游。
	public static String nl2SqlPlan(String instruction) {
		if (instruction == null || instruction.isBlank()) {
			throw new IllegalArgumentException("NL2SQL instruction must not be blank");
		}
		ExecutionStep step = new ExecutionStep();
		ExecutionStep.ToolParameters parameters = new ExecutionStep.ToolParameters();
		parameters.setInstruction(instruction);
		step.setStep(1);
		step.setToolToUse(Constant.SQL_GENERATE_NODE);
		step.setToolParameters(parameters);
		Plan plan = new Plan();
		plan.setThoughtProcess("根据问题生成SQL");
		plan.setExecutionPlan(List.of(step));
		try {
			return JsonUtil.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(plan);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize NL2SQL plan", e);
		}
	}

}
