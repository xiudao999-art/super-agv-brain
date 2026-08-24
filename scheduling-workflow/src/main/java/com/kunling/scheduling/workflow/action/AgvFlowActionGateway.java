package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class AgvFlowActionGateway {
    private final ActionParameterSetService parameterSetService;


    public AgvFlowActionGateway(ActionParameterSetService parameterSetService) {
        this.parameterSetService = parameterSetService;
    }


    public  List<ActionParameterSetView> actions(String key) {
        return parameterSetService.list(key);
    }
}
