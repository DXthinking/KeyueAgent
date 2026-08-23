package org.example.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Configuration
public class SentinelConfig {

    @PostConstruct
    public void loadRules() {
        FlowRule chat = flow("chatStream", 10);
        FlowRule aiOps = flow("aiOpsAnalyze", 5);
        FlowRule tool = flow("toolCall", 20);
        FlowRuleManager.loadRules(List.of(chat, aiOps, tool));

        DegradeRule llm = new DegradeRule("llmCall")
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setCount(30_000)
                .setTimeWindow(10);
        DegradeRule tools = new DegradeRule("toolCall")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5)
                .setMinRequestAmount(5)
                .setTimeWindow(10);
        DegradeRuleManager.loadRules(List.of(llm, tools));
    }

    private FlowRule flow(String resource, double count) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }
}
