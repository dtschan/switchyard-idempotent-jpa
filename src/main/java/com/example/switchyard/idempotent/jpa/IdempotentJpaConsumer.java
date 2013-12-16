/*
 * Copyright 2013 Daniel Tschan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.switchyard.idempotent.jpa;

import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.idempotent.jpa.JpaMessageIdRepository;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotent consumer using {@link JpaMessageIdRepository}.
 * 
 * @author Daniel Tschan <tschan@puzzle.ch>
 */
public class IdempotentJpaConsumer extends RouteBuilder {
    @Override
    public void configure() {
        from("switchyard://" + IdempotentJpaConsumer.class.getSimpleName()).idempotentConsumer(body(),
                jpaMessageIdRepository(jpaTemplate(), transactionTemplate(), "TestMessageIdRepository")).log(
                "Received message for 'IdempotentJpaConsumer' : ${body}");
    }

    private JpaTemplate jpaTemplate() {
        // Work around missing CDI support in routes
        BeanManager beanManager = getBeanManager();

        EntityManagerFactory entityManagerFactory = null;

        Set<Bean<?>> beans = beanManager.getBeans(EntityManagerFactory.class);
        if (beans != null && !beans.isEmpty()) {
            Bean<?> bean = beans.iterator().next();
            CreationalContext<?> context = beanManager.createCreationalContext(bean);
            entityManagerFactory = (EntityManagerFactory) beanManager.getReference(bean, Object.class, context);
        }

        return new JpaTemplate(entityManagerFactory);
    }

    private static TransactionTemplate transactionTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JtaTransactionManager(getTransactionManager()));

        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        return transactionTemplate;
    }

    private static JpaMessageIdRepository jpaMessageIdRepository(JpaTemplate jpaTemplate,
            TransactionTemplate transactionTemplate, String processorName) {
        return new JpaMessageIdRepository(jpaTemplate, transactionTemplate, processorName);
    }

    private static TransactionManager getTransactionManager() {
        try {
            return (TransactionManager) new InitialContext().lookup("java:jboss/TransactionManager");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private static BeanManager getBeanManager() {
        try {
            return (BeanManager) new InitialContext().lookup("java:comp/BeanManager");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }
}
