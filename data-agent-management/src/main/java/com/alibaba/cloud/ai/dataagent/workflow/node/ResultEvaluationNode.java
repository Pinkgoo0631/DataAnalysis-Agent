package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ResultEvaluationNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        //获取原始query
        //获取最终结果
        return Map.of();
    }
}
