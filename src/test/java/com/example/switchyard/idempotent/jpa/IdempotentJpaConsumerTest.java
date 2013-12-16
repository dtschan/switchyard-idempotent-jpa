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

import java.io.File;
import java.util.zip.ZipFile;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.apache.camel.Exchange;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.switchyard.component.bean.Reference;

/**
 * Arquillian test for {@link IdempotentJpaConsumer}.
 * 
 * The test assumes that "mvn package" has been run and that JBOSS_HOME points to an instance of JBoss Fuse ServiceWorks
 * 6 Beta."
 * 
 * @author Daniel Tschan <tschan@puzzle.ch>
 */
@RunWith(Arquillian.class)
public class IdempotentJpaConsumerTest {
    private static final String JAR_FILE = "target/switchyard-idempotent-jpa-0.0.1-SNAPSHOT.jar";

    @PersistenceContext
    EntityManager entityManager;

    // mappedName needed because of ARQ-538?
    @Resource(mappedName = "java:jboss/UserTransaction")
    UserTransaction utx;

    @Inject
    @Reference("IdempotentJpaConsumer")
    TestService testService;

    @Deployment
    public static Archive<?> createTestArchive() {
        File artifact = new File(JAR_FILE);
        try {
            JavaArchive archive =
                    ShrinkWrap.create(ZipImporter.class, artifact.getName()).importFrom(new ZipFile(artifact))
                            .as(JavaArchive.class);
            archive.delete("META-INF/switchyard.xml");
            archive.addAsManifestResource("META-INF/switchyard.xml", "switchyard.xml");

            return archive;
        } catch (Exception e) {
            throw new RuntimeException(JAR_FILE + " not found. Do \"mvn package\" before the test", e);
        }
    }

    @Test
    public void testIdempotentConsumer() throws Exception {
        try {
            utx.begin();

            entityManager.joinTransaction();

            Assert.assertNull(testService.process("Message 1").getContext().getProperty(Exchange.DUPLICATE_MESSAGE));
            Assert.assertNull(testService.process("Message 2").getContext().getProperty(Exchange.DUPLICATE_MESSAGE));
            Assert.assertNotNull(testService.process("Message 1").getContext().getProperty(Exchange.DUPLICATE_MESSAGE));
        } finally {
            utx.rollback();
        }
    }
}
