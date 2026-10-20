package uk.gov.justice.laa.datauserapi.security.rbac;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.util.function.SingletonSupplier;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registers the RBAC method interceptor. Any bean method that accepts a message annotated with
 * {@link RequiresPermission} is authorised before invocation. Ordered right after {@code @PreAuthorize}
 * so it runs before {@code @Transactional} opens a transaction.
 */
@Configuration(proxyBeanMethods = false)
public class RbacMethodSecurityConfig {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor requiresPermissionAdvisor(BeanFactory beanFactory,
                                             ObjectProvider<GlobalAuthorizationPolicy<?>> globalPolicies) {
        // Policies are resolved lazily (on first use) so this infrastructure bean doesn't eagerly
        // initialise application beans.
        Supplier<List<GlobalAuthorizationPolicy<?>>> globals =
                SingletonSupplier.of(() -> globalPolicies.orderedStream().toList());
        AuthorizationManagerBeforeMethodInterceptor interceptor = new AuthorizationManagerBeforeMethodInterceptor(
                new RequiresPermissionPointcut(),
                new RequiresPermissionAuthorizationManager(beanFactory::getBean, globals));
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + 1);
        return interceptor;
    }

    static final class RequiresPermissionPointcut extends StaticMethodMatcherPointcut {
        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getParameterCount() > 0
                    && RequiresPermissionAuthorizationManager.findRequirement(method, targetClass).isPresent();
        }
    }
}
