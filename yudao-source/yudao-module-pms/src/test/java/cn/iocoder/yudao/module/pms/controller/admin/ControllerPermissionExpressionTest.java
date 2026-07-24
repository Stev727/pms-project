package cn.iocoder.yudao.module.pms.controller.admin;

import cn.iocoder.yudao.module.pms.controller.admin.changerecord.ChangeRecordController;
import cn.iocoder.yudao.module.pms.controller.admin.project.ProjectController;
import cn.iocoder.yudao.module.pms.controller.admin.project.vo.ProjectCreateBundleReqVO;
import cn.iocoder.yudao.module.pms.controller.admin.task.TaskController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerPermissionExpressionTest {
    @Test
    void newWorkflowEndpointsUseQuotedPermissionExpressions() throws Exception {
        assertPermission(ProjectController.class, "createBundle", "@ss.hasPermission('pms:project:create')", ProjectCreateBundleReqVO.class);
        assertPermission(TaskController.class, "submitCompletion", "@ss.hasPermission('pms:task:update')", Long.class);
        assertPermission(TaskController.class, "reviewCompletion", "@ss.hasPermission('pms:task:update')", Long.class, boolean.class);
        assertPermission(ChangeRecordController.class, "review", "@ss.hasPermission('pms:change:update')", Long.class, boolean.class);
        assertPermission(ChangeRecordController.class, "execute", "@ss.hasPermission('pms:change:update')", Long.class);
    }

    private static void assertPermission(Class<?> controller, String methodName, String expected,
                                         Class<?>... parameterTypes) throws Exception {
        Method method = controller.getDeclaredMethod(methodName, parameterTypes);
        assertEquals(expected, method.getAnnotation(PreAuthorize.class).value());
    }
}
