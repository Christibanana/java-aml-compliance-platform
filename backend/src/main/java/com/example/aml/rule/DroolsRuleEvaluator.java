package com.example.aml.rule;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DroolsRuleEvaluator {
    private final KieBase kieBase;

    public DroolsRuleEvaluator() {
        KieHelper helper = new KieHelper();
        helper.addResource(ResourceFactory.newClassPathResource("rules/transaction-monitoring.drl"),
                ResourceType.DRL);
        try {
            this.kieBase = helper.build(ExecutableModelProject.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Drools 规则编译失败", exception);
        }
    }

    public int evaluate(List<RuleEvaluationFact> facts) {
        KieSession session = kieBase.newKieSession();
        try {
            facts.forEach(session::insert);
            return session.fireAllRules();
        } finally {
            session.dispose();
        }
    }

    public String runtimeVersion() {
        String version = KieServices.class.getPackage().getImplementationVersion();
        return version == null ? "10.1.0" : version;
    }
}
