package com.guying.controller;



import com.guying.task.ExperienceAnalysisTask;
import com.guying.task.FaqEvolutionTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Slf4j
@Tag(name = "数字人管理")
@RequestMapping("/v1/admins/digital-human")
public class DigitalHumanController {
    @Autowired
    private FaqEvolutionTask faqEvolutionTask;

    @Autowired
    private ExperienceAnalysisTask  experienceAnalysisTask;

    @Operation(summary = "测试faq任务")
    // 在任意已有的 Controller 里临时加一行
    @GetMapping("/test/faq-task")
    public String testFaq() {
        faqEvolutionTask.execute();
        return "ok";
    }
    @Operation(summary = "测试Analysis任务")
    // 在任意已有的 Controller 里临时加一行
    @GetMapping("/test/anlysis-task")
    public String testAnalysisTask(){
        experienceAnalysisTask.generateWeeklySuggestion();
        return "ok";
    }
}
